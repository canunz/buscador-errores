import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CatalogoService } from '../../core/services/catalogo.service';
import { PruebaItem } from '../../core/models/catalogo.model';
import { CatalogoTabsComponent } from '../../shared/ui/catalogo-tabs.component';

@Component({
  selector: 'app-pruebas',
  standalone: true,
  imports: [ReactiveFormsModule, CatalogoTabsComponent],
  templateUrl: './pruebas.component.html',
  styleUrl: '../../shared/ui/catalogo-page.css',
})
export class PruebasComponent {
  private readonly catalogo = inject(CatalogoService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);

  get titulo(): string {
    return this.router.url.includes('procedimientos') ? 'Procedimientos' : 'Pruebas';
  }

  modalOpen = false;
  editId: number | null = null;

  form = this.fb.nonNullable.group({
    descripcion: ['', Validators.required],
    resultadoEsperado: [''],
    activo: [true],
  });

  get items(): PruebaItem[] {
    return this.catalogo.pruebas();
  }

  openCreate(): void {
    this.editId = null;
    this.form.reset({ descripcion: '', resultadoEsperado: '', activo: true });
    this.modalOpen = true;
  }

  openEdit(item: PruebaItem): void {
    this.editId = item.id;
    this.form.reset({
      descripcion: item.descripcion,
      resultadoEsperado: item.resultadoEsperado,
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
    this.catalogo.guardarPrueba({
      id: this.editId ?? undefined,
      descripcion: raw.descripcion.trim(),
      resultadoEsperado: raw.resultadoEsperado.trim(),
      activo: raw.activo,
    });
    this.modalOpen = false;
  }

  eliminar(item: PruebaItem): void {
    if (confirm(`¿Eliminar la prueba "${item.descripcion}"?`)) {
      this.catalogo.eliminarPrueba(item.id);
    }
  }
}
