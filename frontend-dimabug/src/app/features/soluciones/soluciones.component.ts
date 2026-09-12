import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { CatalogoService } from '../../core/services/catalogo.service';
import { SolucionItem } from '../../core/models/catalogo.model';
import { CatalogoTabsComponent } from '../../shared/ui/catalogo-tabs.component';

@Component({
  selector: 'app-soluciones',
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule, CatalogoTabsComponent],
  templateUrl: './soluciones.component.html',
  styleUrl: '../../shared/ui/catalogo-page.css',
})
export class SolucionesComponent {
  private readonly catalogo = inject(CatalogoService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);

  q = this.route.snapshot.queryParamMap.get('q') || '';
  modalOpen = false;
  editId: number | null = null;

  form = this.fb.nonNullable.group({
    nombre: ['', Validators.required],
    pasos: [''],
    anexos: [''],
    asignaciones: [''],
  });

  get items(): SolucionItem[] {
    const term = this.q.trim().toLowerCase();
    return this.catalogo.soluciones().filter((item) => {
      if (!term) {
        return true;
      }
      return [item.nombre, item.pasos, item.anexos, item.asignaciones.join(' ')]
        .join(' ')
        .toLowerCase()
        .includes(term);
    });
  }

  openCreate(): void {
    this.editId = null;
    this.form.reset({ nombre: '', pasos: '', anexos: '', asignaciones: '' });
    this.modalOpen = true;
  }

  openEdit(item: SolucionItem): void {
    this.editId = item.id;
    this.form.reset({
      nombre: item.nombre,
      pasos: item.pasos,
      anexos: item.anexos,
      asignaciones: item.asignaciones.join(', '),
    });
    this.modalOpen = true;
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    this.catalogo.guardarSolucion({
      id: this.editId ?? undefined,
      nombre: raw.nombre.trim(),
      pasos: raw.pasos.trim(),
      anexos: raw.anexos.trim(),
      asignaciones: raw.asignaciones
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean),
    });
    this.modalOpen = false;
  }

  eliminar(item: SolucionItem): void {
    if (confirm(`¿Eliminar la solución "${item.nombre}"?`)) {
      this.catalogo.eliminarSolucion(item.id);
    }
  }

  recortar(texto: string, max = 140): string {
    if (!texto) {
      return '—';
    }
    return texto.length > max ? `${texto.slice(0, max)}…` : texto;
  }
}
