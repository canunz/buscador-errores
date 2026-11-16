import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { CatalogoRef, Conocimiento, ConocimientoRequest } from '../../core/models/conocimiento.model';
import { ClasificacionService } from '../../core/services/clasificacion.service';
import { ConocimientoService } from '../../core/services/conocimiento.service';
import { AyudaFranjaComponent } from '../../shared/ui/ayuda-franja.component';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';
import { ConocimientoDetalleComponent } from './conocimiento-detalle.component';

@Component({
  selector: 'app-conocimiento-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, LoadingModalComponent, ConocimientoDetalleComponent, AyudaFranjaComponent],
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
  noDisponible = false;
  error = '';
  frecuenciasDisponibles = false;
  pestana: 'ficha' | 'guia' = 'ficha';

  hardwares: CatalogoRef[] = [];
  sistemas: CatalogoRef[] = [];
  modulos: CatalogoRef[] = [];
  frecuencias: CatalogoRef[] = [];
  private actual: Conocimiento | null = null;

  form = this.fb.nonNullable.group({
    titulo: ['', [Validators.required, Validators.maxLength(150)]],
    descripcion: ['', Validators.required],
    hardwareId: this.fb.control<number | null>(null),
    sistemaId: this.fb.control<number | null>(null),
    moduloId: this.fb.control<number | null>(null),
    frecuenciaId: this.fb.control<number | null>(null),
    comentario: ['', [Validators.maxLength(500)]],
  });

  get esEdicion(): boolean {
    return this.editId != null;
  }

  ngOnInit(): void {
    const rawId = this.route.snapshot.paramMap.get('id');
    this.editId = rawId ? Number(rawId) : null;
    this.hidratarDesdeCache();
    this.form.controls.hardwareId.valueChanges.subscribe((hardwareId) => this.onHardwareChange(hardwareId));
    this.form.controls.sistemaId.valueChanges.subscribe((sistemaId) => this.onSistemaChange(sistemaId));
    this.clasificacion.precargarClasificacion();
    this.cargarInicial();
  }

  guardar(): void {
    this.error = '';
    if (this.form.controls.titulo.invalid || this.form.controls.descripcion.invalid) {
      this.form.controls.titulo.markAsTouched();
      this.form.controls.descripcion.markAsTouched();
      this.error = 'El título y la descripción son obligatorios.';
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

  private hidratarDesdeCache(): void {
    this.hardwares = this.clasificacion.snapshotHardware();
    const freqs = this.clasificacion.snapshotFrecuencias();
    this.frecuencias = freqs.items;
    this.frecuenciasDisponibles = freqs.disponible;
  }

  private cargarInicial(): void {
    this.loading = !!this.editId;

    this.clasificacion.listarHardware(true).subscribe({
      next: (hardwares) => {
        this.hardwares = hardwares;
        this.conservarReferencias();
      },
      error: (err: Error) => (this.error = err.message),
    });

    this.clasificacion.listarFrecuencias(true).subscribe({
      next: (frecuencias) => {
        this.frecuencias = frecuencias.items;
        this.frecuenciasDisponibles = frecuencias.disponible;
        this.conservarReferencias();
      },
    });

    if (!this.editId) {
      return;
    }

    this.conocimientos.obtenerPorId(this.editId, true).subscribe({
      next: (actual) => {
        this.actual = actual;
        this.conservarReferencias();
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
        this.noDisponible = err.message === 'El conocimiento no está disponible.';
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

    // Instantáneo desde caché, luego refresco de red.
    this.sistemas = this.clasificacion.snapshotSistemas(hardwareId);
    if (sistemaId) {
      this.modulos = this.clasificacion.snapshotModulos(sistemaId);
    }
    this.conservarReferencias();

    const sistemas$ = this.clasificacion.sistemasDeHardware(hardwareId);
    const modulos$ = sistemaId ? this.clasificacion.modulosDeSistema(sistemaId) : of([] as CatalogoRef[]);

    forkJoin({ sistemas: sistemas$, modulos: modulos$ }).subscribe({
      next: ({ sistemas, modulos }) => {
        this.sistemas = sistemas;
        if (!sistemaId) {
          this.modulos = [];
          this.loading = false;
          return;
        }
        this.modulos = modulos;
        this.conservarReferencias();
        this.loading = false;
      },
      error: (err: Error) => {
        this.loading = false;
        this.error = err.message;
        this.conservarReferencias();
      },
    });
  }

  private onHardwareChange(hardwareId: number | null): void {
    this.form.controls.sistemaId.setValue(null, { emitEvent: false });
    this.form.controls.moduloId.setValue(null, { emitEvent: false });
    this.modulos = [];
    if (!hardwareId) {
      this.sistemas = [];
      return;
    }
    // Primero lo que ya está en caché (inmediato al desplegar).
    this.sistemas = this.clasificacion.snapshotSistemas(hardwareId);
    this.clasificacion.sistemasDeHardware(hardwareId).subscribe({
      next: (sistemas) => {
        this.sistemas = sistemas;
        for (const s of sistemas) {
          this.clasificacion.modulosDeSistema(s.id).subscribe({ error: () => undefined });
        }
      },
      error: (err: Error) => (this.error = err.message),
    });
  }

  private onSistemaChange(sistemaId: number | null): void {
    this.form.controls.moduloId.setValue(null, { emitEvent: false });
    if (!sistemaId) {
      this.modulos = [];
      return;
    }
    this.modulos = this.clasificacion.snapshotModulos(sistemaId);
    // force si la caché quedó vacía (p. ej. módulos recién cargados en BD)
    this.clasificacion.modulosDeSistema(sistemaId, this.modulos.length === 0).subscribe({
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

  private conservarReferencias(): void {
    const actual = this.actual;
    if (!actual) return;
    const incluir = (lista: CatalogoRef[], id: number | null, nombre: string) =>
      id && !lista.some(item => item.id === id) ? [...lista, { id, nombre: `${nombre} (referencia existente)` }] : lista;
    this.hardwares = incluir(this.hardwares, actual.hardwareId, actual.hardwareNombre);
    this.frecuencias = incluir(this.frecuencias, actual.frecuenciaId, actual.frecuenciaNombre);
    this.frecuenciasDisponibles ||= this.frecuencias.length > 0;
    if (this.form.controls.hardwareId.value === actual.hardwareId) {
      this.sistemas = incluir(this.sistemas, actual.sistemaId, actual.sistemaNombre);
      if (this.form.controls.sistemaId.value === actual.sistemaId) {
        this.modulos = incluir(this.modulos, actual.moduloId, actual.moduloNombre);
      }
    }
  }
}
