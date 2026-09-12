import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CatalogoService } from '../../core/services/catalogo.service';
import { DepartamentoItem } from '../../core/models/catalogo.model';
import { CatalogoTabsComponent } from '../../shared/ui/catalogo-tabs.component';

@Component({
  selector: 'app-departamentos',
  standalone: true,
  imports: [ReactiveFormsModule, CatalogoTabsComponent],
  templateUrl: './departamentos.component.html',
  styleUrl: '../../shared/ui/catalogo-page.css',
})
export class DepartamentosComponent {
  private readonly catalogo = inject(CatalogoService);
  private readonly fb = inject(FormBuilder);

  modalOpen = false;
  editId: number | null = null;

  form = this.fb.nonNullable.group({
    nombre: ['', Validators.required],
    contactos: [''],
    responsables: [''],
    activo: [true],
  });

  get items(): DepartamentoItem[] {
    return this.catalogo.departamentos();
  }

  openCreate(): void {
    this.editId = null;
    this.form.reset({ nombre: '', contactos: '', responsables: '', activo: true });
    this.modalOpen = true;
  }

  openEdit(item: DepartamentoItem): void {
    this.editId = item.id;
    this.form.reset({
      nombre: item.nombre,
      contactos: item.contactos.map((c) => `${c.tipo}: ${c.numero}`).join(', '),
      responsables: item.responsables.join(', '),
      activo: item.activo,
    });
    this.modalOpen = true;
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    this.catalogo.guardarDepartamento({
      id: this.editId ?? undefined,
      nombre: raw.nombre.trim(),
      activo: raw.activo,
      contactos: raw.contactos
        .split(',')
        .map((row) => row.trim())
        .filter(Boolean)
        .map((row) => {
          const [tipo, ...rest] = row.split(':');
          return { tipo: tipo.trim() || 'Contacto', numero: rest.join(':').trim() || row, activo: true };
        }),
      responsables: raw.responsables
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean),
    });
    this.modalOpen = false;
  }

  eliminar(item: DepartamentoItem): void {
    if (confirm(`¿Eliminar el departamento "${item.nombre}"?`)) {
      this.catalogo.eliminarDepartamento(item.id);
    }
  }
}
