import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CatalogoService } from '../../core/services/catalogo.service';
import { HardwareItem } from '../../core/models/catalogo.model';
import { CatalogoTabsComponent } from '../../shared/ui/catalogo-tabs.component';

@Component({
  selector: 'app-hardware',
  standalone: true,
  imports: [ReactiveFormsModule, CatalogoTabsComponent],
  templateUrl: './hardware.component.html',
  styleUrl: '../../shared/ui/catalogo-page.css',
})
export class HardwareComponent {
  private readonly catalogo = inject(CatalogoService);
  private readonly fb = inject(FormBuilder);

  modalOpen = false;
  editId: number | null = null;

  form = this.fb.nonNullable.group({
    nombre: ['', Validators.required],
    so: [''],
    sistemas: [''],
  });

  get items(): HardwareItem[] {
    return this.catalogo.hardware();
  }

  openCreate(): void {
    this.editId = null;
    this.form.reset({ nombre: '', so: '', sistemas: '' });
    this.modalOpen = true;
  }

  openEdit(item: HardwareItem): void {
    this.editId = item.id;
    this.form.reset({
      nombre: item.nombre,
      so: item.so,
      sistemas: item.sistemas.join(', '),
    });
    this.modalOpen = true;
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    this.catalogo.guardarHardware({
      id: this.editId ?? undefined,
      nombre: raw.nombre.trim(),
      so: raw.so.trim(),
      sistemas: raw.sistemas
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean),
    });
    this.modalOpen = false;
  }

  eliminar(item: HardwareItem): void {
    if (confirm(`¿Eliminar el hardware "${item.nombre}"?`)) {
      this.catalogo.eliminarHardware(item.id);
    }
  }
}
