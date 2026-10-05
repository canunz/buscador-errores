import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ClasificacionService } from '../../core/services/clasificacion.service';
import { HardwareItem } from '../../core/models/catalogo.model';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';

@Component({
  selector: 'app-hardware',
  standalone: true,
  imports: [LoadingModalComponent],
  templateUrl: './hardware.component.html',
  styleUrls: ['../../shared/ui/catalogo-page.css', './hardware.component.css'],
})
export class HardwareComponent implements OnInit {
  private readonly clasificacion = inject(ClasificacionService);
  private readonly destroyRef = inject(DestroyRef);

  items: HardwareItem[] = [];
  loading = false;
  error = '';
  detalle: HardwareItem | null = null;
  pendienteEliminar: HardwareItem | null = null;

  ngOnInit(): void {
    this.cargar();
  }

  ver(item: HardwareItem): void {
    this.detalle = item;
  }

  cerrarDetalle(): void {
    this.detalle = null;
  }

  pedirEliminar(item: HardwareItem): void {
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
    this.clasificacion.ocultarHardware(item.id);
    this.items = this.items.filter((actual) => actual.id !== item.id);
    if (this.detalle?.id === item.id) {
      this.detalle = null;
    }
    this.pendienteEliminar = null;
  }

  cargar(force = false): void {
    this.loading = !this.items.length;
    this.error = '';
    let first = true;
    this.clasificacion
      .listarHardwareDetalle(force)
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
          if (!this.items.length) {
            this.items = [];
          }
          this.loading = false;
        },
      });
  }
}
