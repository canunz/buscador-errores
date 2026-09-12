import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CatalogoService } from '../../core/services/catalogo.service';
import { ErrorItem, FRECUENCIAS } from '../../core/models/catalogo.model';
import { CatalogoTabsComponent } from '../../shared/ui/catalogo-tabs.component';

@Component({
  selector: 'app-error-nuevo',
  standalone: true,
  imports: [ReactiveFormsModule, CatalogoTabsComponent],
  templateUrl: './error-nuevo.component.html',
  styleUrls: ['../../shared/ui/catalogo-page.css', './error-nuevo.component.css'],
})
export class ErrorNuevoComponent {
  private readonly catalogo = inject(CatalogoService);
  private readonly fb = inject(FormBuilder);

  readonly frecuencias = FRECUENCIAS;
  vista: 'lista' | 'form' = 'lista';
  editId: number | null = null;

  form = this.fb.nonNullable.group({
    descripcion: ['', Validators.required],
    hardwareId: [0],
    sistema: [''],
    modulo: [''],
    frecuencia: [''],
    usuarioContexto: [''],
    causa: [''],
    comentarios: [''],
    solucionIds: this.fb.nonNullable.control<number[]>([]),
    pruebaIds: this.fb.nonNullable.control<number[]>([]),
  });

  get errores(): ErrorItem[] {
    return [...this.catalogo.errores()].reverse();
  }

  get hardware() {
    return this.catalogo.hardware();
  }

  get sistemas(): string[] {
    const id = Number(this.form.controls.hardwareId.value) || null;
    return this.catalogo.sistemasDeHardware(id);
  }

  get soluciones() {
    return this.catalogo.soluciones();
  }

  get pruebas() {
    return this.catalogo.pruebas().filter((p) => p.activo);
  }

  nombreHardware(id: number | null): string {
    return this.hardware.find((item) => item.id === id)?.nombre || 'Sin hardware';
  }

  nombresSoluciones(ids: number[]): string {
    const names = this.soluciones.filter((item) => ids.includes(item.id)).map((item) => item.nombre);
    return names.join(', ') || 'Sin soluciones';
  }

  abrirNuevo(): void {
    this.editId = null;
    this.form.reset({
      descripcion: '',
      hardwareId: 0,
      sistema: '',
      modulo: '',
      frecuencia: '',
      usuarioContexto: '',
      causa: '',
      comentarios: '',
      solucionIds: [],
      pruebaIds: [],
    });
    this.vista = 'form';
  }

  abrirEditar(item: ErrorItem): void {
    this.editId = item.id;
    this.form.reset({
      descripcion: item.descripcion,
      hardwareId: item.hardwareId || 0,
      sistema: item.sistema,
      modulo: item.modulo,
      frecuencia: item.frecuencia,
      usuarioContexto: item.usuarioContexto,
      causa: item.causa,
      comentarios: item.comentarios,
      solucionIds: [...item.solucionIds],
      pruebaIds: [...item.pruebaIds],
    });
    this.vista = 'form';
  }

  volver(): void {
    this.vista = 'lista';
  }

  eliminar(item: ErrorItem): void {
    if (confirm(`¿Eliminar el error "${item.descripcion}"?`)) {
      this.catalogo.eliminarError(item.id);
    }
  }

  toggleId(control: 'solucionIds' | 'pruebaIds', id: number, checked: boolean): void {
    const current = this.form.controls[control].value;
    const next = checked ? [...new Set([...current, id])] : current.filter((item) => item !== id);
    this.form.controls[control].setValue(next);
  }

  isChecked(control: 'solucionIds' | 'pruebaIds', id: number): boolean {
    return this.form.controls[control].value.includes(id);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    this.catalogo.guardarError({
      id: this.editId ?? undefined,
      descripcion: raw.descripcion.trim(),
      hardwareId: raw.hardwareId || null,
      sistema: raw.sistema,
      modulo: raw.modulo.trim(),
      frecuencia: raw.frecuencia,
      usuarioContexto: raw.usuarioContexto.trim(),
      causa: raw.causa.trim(),
      comentarios: raw.comentarios.trim(),
      solucionIds: raw.solucionIds,
      pruebaIds: raw.pruebaIds,
    });
    this.vista = 'lista';
  }
}
