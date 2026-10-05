import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { OrganizacionService } from '../../core/services/organizacion.service';
import { DepartamentoItem } from '../../core/models/catalogo.model';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';
@Component({
  selector: 'app-departamentos',
  standalone: true,
  imports: [LoadingModalComponent],
  templateUrl: './departamentos.component.html',
  styleUrls: ['../../shared/ui/catalogo-page.css', './departamentos.component.css'],
})
export class DepartamentosComponent implements OnInit {
  private readonly organizacion = inject(OrganizacionService);
  private readonly destroyRef = inject(DestroyRef);

  items: DepartamentoItem[] = [];
  loading = false;
  error = '';
  detalle: DepartamentoItem | null = null;
  pendienteEstado: DepartamentoItem | null = null;
  pendienteEliminar: DepartamentoItem | null = null;

  ver(item: DepartamentoItem): void {
    this.detalle = item;
  }

  cerrarDetalle(): void {
    this.detalle = null;
  }

  ngOnInit(): void {
    this.cargar();
  }

  pedirCambioEstado(item: DepartamentoItem): void {
    this.pendienteEstado = item;
  }

  cancelarEstado(): void {
    this.pendienteEstado = null;
  }

  pedirEliminar(item: DepartamentoItem): void {
    this.pendienteEliminar = item;
  }

  cancelarEliminar(): void {
    this.pendienteEliminar = null;
  }

  confirmarEliminar(): void {
    const item = this.pendienteEliminar;
    if (!item) {
      return;
    }
    this.organizacion.ocultarDepartamento(item.id);
    this.items = this.items.filter((actual) => actual.id !== item.id);
    if (this.detalle?.id === item.id) {
      this.detalle = null;
    }
    this.pendienteEliminar = null;
  }

  confirmarEstado(): void {
    const item = this.pendienteEstado;
    if (!item) {
      return;
    }
    const activo = !item.activo;
    this.organizacion.marcarDepartamentoActivo(item.id, activo);
    this.items = this.items.map((actual) => (actual.id === item.id ? { ...actual, activo } : actual));
    this.pendienteEstado = null;
  }

  cargar(force = false): void {
    this.loading = !this.items.length;
    this.error = '';
    let first = true;
    this.organizacion
      .listarDepartamentosDetalle(force)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (items) => {
          this.items = items;
          if (first) {
            first = false;
            this.loading = false;
          }
        },
        error: (err: Error) => {
          this.error = err.message;
          this.loading = false;
        },
      });
  }
}
