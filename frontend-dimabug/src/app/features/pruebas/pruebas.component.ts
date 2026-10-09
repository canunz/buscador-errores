import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { OrganizacionService } from '../../core/services/organizacion.service';
import { PruebaItem } from '../../core/models/catalogo.model';
import { AyudaFranjaComponent } from '../../shared/ui/ayuda-franja.component';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';
@Component({
  selector: 'app-pruebas',
  standalone: true,
  imports: [LoadingModalComponent, AyudaFranjaComponent],
  templateUrl: './pruebas.component.html',
  styleUrls: ['../../shared/ui/catalogo-page.css', './pruebas.component.css'],
})
export class PruebasComponent implements OnInit {
  private readonly organizacion = inject(OrganizacionService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  items: PruebaItem[] = [];
  loading = false;
  error = '';
  detalle: PruebaItem | null = null;
  pendienteEstado: PruebaItem | null = null;
  pendienteEliminar: PruebaItem | null = null;

  ver(item: PruebaItem): void {
    this.detalle = item;
  }

  cerrarDetalle(): void {
    this.detalle = null;
  }

  get titulo(): string {
    return this.router.url.includes('procedimientos') ? 'Procedimientos' : 'Pruebas';
  }

  pedirCambioEstado(item: PruebaItem): void {
    this.pendienteEstado = item;
  }

  cancelarEstado(): void {
    this.pendienteEstado = null;
  }

  pedirEliminar(item: PruebaItem): void {
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
    this.organizacion.ocultarPrueba(item.id);
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
    this.organizacion.marcarPruebaActiva(item.id, activo);
    this.items = this.items.map((actual) => (actual.id === item.id ? { ...actual, activo } : actual));
    this.pendienteEstado = null;
  }

  ngOnInit(): void {
    // Pintar de inmediato lo que ya esté en sesión.
    const cached = this.organizacion.snapshotPruebas();
    if (cached.length) {
      this.items = cached.map((p) => ({
        id: p.id,
        descripcion: p.descripcion,
        resultadoEsperado: p.resultadoEsperado,
        activo: true,
      }));
    }
    this.cargar();
  }

  cargar(): void {
    this.loading = !this.items.length;
    this.error = '';
    this.organizacion
      .listarPruebasDetalle(false)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (items) => {
          this.items = items;
          this.loading = false;
        },
        error: (err: Error) => {
          this.error = err.message;
          this.loading = false;
        },
      });
  }
}
