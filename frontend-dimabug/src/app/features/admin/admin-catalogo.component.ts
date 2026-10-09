import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { AdminMaestrosService, MaestroTipo } from '../../core/services/admin-maestros.service';
import { AyudaFranjaComponent } from '../../shared/ui/ayuda-franja.component';

const META: Record<MaestroTipo, { titulo: string; singular: string; seccion: string; extra?: string; activo?: boolean }> = {
  grupos: { titulo: 'Grupos', singular: 'grupo', seccion: 'Autenticación y autorización' },
  frecuencias: { titulo: 'Frecuencias', singular: 'frecuencia', seccion: 'Buscador de Errores' },
  sistemas: { titulo: 'Sistemas', singular: 'sistema', seccion: 'Buscador de Errores' },
  modulos: { titulo: 'Módulos', singular: 'módulo', seccion: 'Buscador de Errores', extra: 'Sistema' },
  responsables: { titulo: 'Responsables', singular: 'responsable', seccion: 'Buscador de Errores', extra: 'Departamento / cargo', activo: true },
};

@Component({
  selector: 'app-admin-catalogo',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, AyudaFranjaComponent],
  templateUrl: './admin-catalogo.component.html',
  styleUrls: ['../../shared/ui/catalogo-page.css', './admin-catalogo.component.css'],
})
export class AdminCatalogoComponent {
  private readonly maestros = inject(AdminMaestrosService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  private readonly tipo = toSignal(
    this.route.paramMap.pipe(map((params) => (params.get('tipo') || 'grupos') as MaestroTipo)),
    { initialValue: (this.route.snapshot.paramMap.get('tipo') || 'grupos') as MaestroTipo },
  );

  readonly meta = computed(() => META[this.tipo()] ?? META.grupos);
  readonly items = computed(() => this.maestros.listar(this.tipo()));

  modalOpen = false;
  editId: number | null = null;
  detalle: { id: number; nombre: string; extra?: string; activo?: boolean } | null = null;
  pendienteEstado: { id: number; nombre: string; activo: boolean } | null = null;

  ver(item: { id: number; nombre: string; extra?: string; activo?: boolean }): void {
    this.detalle = item;
  }

  cerrarDetalle(): void {
    this.detalle = null;
  }

  form = this.fb.nonNullable.group({
    nombre: ['', Validators.required],
    extra: [''],
    activo: [true],
  });

  openCreate(): void {
    this.editId = null;
    this.form.reset({ nombre: '', extra: '', activo: true });
    this.modalOpen = true;
  }

  openEdit(id: number, nombre: string, extra = '', activo = true): void {
    this.editId = id;
    this.form.reset({ nombre, extra, activo });
    this.modalOpen = true;
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    this.maestros.guardar(this.tipo(), {
      id: this.editId ?? undefined,
      nombre: raw.nombre.trim(),
      extra: raw.extra.trim() || undefined,
      activo: this.meta().activo ? raw.activo : undefined,
    });
    this.modalOpen = false;
  }

  pedirCambioEstado(id: number, nombre: string, activo: boolean): void {
    this.pendienteEstado = { id, nombre, activo };
  }

  cancelarEstado(): void {
    this.pendienteEstado = null;
  }

  confirmarEstado(): void {
    const item = this.pendienteEstado;
    if (!item) {
      return;
    }
    this.maestros.marcarActivo(this.tipo(), item.id, !item.activo);
    this.pendienteEstado = null;
  }

  eliminar(id: number, nombre: string): void {
    if (confirm(`¿Eliminar "${nombre}"?`)) {
      this.maestros.eliminar(this.tipo(), id);
    }
  }
}
