import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { CatalogoRef, ConocimientoRequest } from '../../core/models/conocimiento.model';
import { ClasificacionService } from '../../core/services/clasificacion.service';
import { ConocimientoService } from '../../core/services/conocimiento.service';

@Component({
  selector: 'app-conocimiento-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './conocimiento-form.component.html',
  styleUrls: ['./conocimiento.component.css', './conocimiento-form.component.css'],
})
export class ConocimientoFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly conocimientos = inject(ConocimientoService);
  private readonly clasificacion = inject(ClasificacionService);

  editId: number | null = null;
  loading = false;
  saving = false;
  error = '';
  frecuenciasDisponibles = false;

  hardwares: CatalogoRef[] = [];
  sistemas: CatalogoRef[] = [];
  modulos: CatalogoRef[] = [];
  frecuencias: CatalogoRef[] = [];

  form = this.fb.nonNullable.group({
    titulo: ['', Validators.required],
    descripcion: [''],
    hardwareId: this.fb.control<number | null>(null),
    sistemaId: this.fb.control<number | null>(null),
    moduloId: this.fb.control<number | null>(null),
    frecuenciaId: this.fb.control<number | null>(null),
    comentario: [''],
  });

  get esEdicion(): boolean {
    return this.editId != null;
  }

  ngOnInit(): void {
    const rawId = this.route.snapshot.paramMap.get('id');
    this.editId = rawId ? Number(rawId) : null;
    this.form.controls.hardwareId.valueChanges.subscribe((hardwareId) => this.onHardwareChange(hardwareId));
    this.form.controls.sistemaId.valueChanges.subscribe((sistemaId) => this.onSistemaChange(sistemaId));
    this.cargarInicial();
  }

  guardar(): void {
    this.error = '';
    if (this.form.controls.titulo.invalid) {
      this.form.controls.titulo.markAsTouched();
      this.error = 'El título es obligatorio.';
      return;
    }
    this.saving = true;
    const request = this.toRequest();
    const req$ =
      this.editId != null
        ? this.conocimientos.modificar(this.editId, request)
        : this.conocimientos.crear(request);

    req$.subscribe({
      next: (item) => this.router.navigate(['/conocimiento', item.id]),
      error: (err: Error) => {
        this.saving = false;
        this.error = err.message;
      },
    });
  }

  private cargarInicial(): void {
    this.loading = true;
    forkJoin({
      hardwares: this.clasificacion.listarHardware(),
      frecuencias: this.clasificacion.listarFrecuencias(),
      actual: this.editId ? this.conocimientos.obtenerPorId(this.editId) : of(null),
    }).subscribe({
      next: ({ hardwares, frecuencias, actual }) => {
        this.hardwares = hardwares;
        this.frecuencias = frecuencias.items;
        this.frecuenciasDisponibles = frecuencias.disponible;
        if (!actual) {
          this.loading = false;
          return;
        }
        this.form.patchValue(
          {
            titulo: actual.titulo,
            descripcion: actual.descripcion,
            hardwareId: actual.hardwareId,
            sistemaId: actual.sistemaId,
            moduloId: actual.moduloId,
            frecuenciaId: actual.frecuenciaId,
            comentario: actual.comentario ?? '',
          },
          { emitEvent: false },
        );
        this.cargarDependientes(actual.hardwareId, actual.sistemaId, actual.moduloId);
      },
      error: (err: Error) => {
        this.loading = false;
        this.error = err.message;
      },
    });
  }

  private cargarDependientes(
    hardwareId: number | null,
    sistemaId: number | null,
    moduloId: number | null,
  ): void {
    if (!hardwareId) {
      this.sistemas = [];
      this.modulos = [];
      this.loading = false;
      return;
    }
    this.clasificacion.sistemasDeHardware(hardwareId).subscribe({
      next: (sistemas) => {
        this.sistemas = sistemas;
        if (sistemaId && !sistemas.some((item) => item.id === sistemaId)) {
          this.form.controls.sistemaId.setValue(null, { emitEvent: false });
          this.form.controls.moduloId.setValue(null, { emitEvent: false });
          this.modulos = [];
          this.loading = false;
          return;
        }
        if (!sistemaId) {
          this.modulos = [];
          this.loading = false;
          return;
        }
        this.clasificacion.modulosDeSistema(sistemaId).subscribe({
          next: (modulos) => {
            this.modulos = modulos;
            if (moduloId && !modulos.some((item) => item.id === moduloId)) {
              this.form.controls.moduloId.setValue(null, { emitEvent: false });
            }
            this.loading = false;
          },
          error: (err: Error) => {
            this.loading = false;
            this.error = err.message;
          },
        });
      },
      error: (err: Error) => {
        this.loading = false;
        this.error = err.message;
      },
    });
  }

  private onHardwareChange(hardwareId: number | null): void {
    this.form.controls.sistemaId.setValue(null, { emitEvent: false });
    this.form.controls.moduloId.setValue(null, { emitEvent: false });
    this.sistemas = [];
    this.modulos = [];
    if (!hardwareId) {
      return;
    }
    this.clasificacion.sistemasDeHardware(hardwareId).subscribe({
      next: (sistemas) => (this.sistemas = sistemas),
      error: (err: Error) => (this.error = err.message),
    });
  }

  private onSistemaChange(sistemaId: number | null): void {
    this.form.controls.moduloId.setValue(null, { emitEvent: false });
    this.modulos = [];
    if (!sistemaId) {
      return;
    }
    this.clasificacion.modulosDeSistema(sistemaId).subscribe({
      next: (modulos) => (this.modulos = modulos),
      error: (err: Error) => (this.error = err.message),
    });
  }

  private toRequest(): ConocimientoRequest {
    const v = this.form.getRawValue();
    return {
      titulo: v.titulo.trim(),
      descripcion: v.descripcion.trim(),
      hardwareId: v.hardwareId,
      sistemaId: v.sistemaId,
      moduloId: v.moduloId,
      frecuenciaId: v.frecuenciaId,
      comentario: v.comentario.trim() ? v.comentario.trim() : null,
    };
  }
}
