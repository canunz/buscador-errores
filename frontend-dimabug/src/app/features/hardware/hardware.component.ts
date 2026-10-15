import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ClasificacionService } from '../../core/services/clasificacion.service';
import { HardwareItem } from '../../core/models/catalogo.model';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AyudaFranjaComponent } from '../../shared/ui/ayuda-franja.component';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';

@Component({
  selector: 'app-hardware',
  standalone: true,
  imports: [RouterLink, LoadingModalComponent, AyudaFranjaComponent],
  templateUrl: './hardware.component.html',
  styleUrls: ['../../shared/ui/catalogo-page.css', './hardware.component.css'],
})
export class HardwareComponent implements OnInit {
  private readonly clasificacion = inject(ClasificacionService);
  private readonly destroyRef = inject(DestroyRef);

  readonly esAdministrador = inject(AuthService).isAdmin;

  items: HardwareItem[] = this.clasificacion.hardwareEnSesion();
  loading = false;
  error = '';
  detalle: HardwareItem | null = null;

  ngOnInit(): void {
    const guardados = this.clasificacion.hardwareEnSesion();
    if (guardados.length) {
      this.items = guardados;
    }
    this.cargar();
  }

  ver(item: HardwareItem): void {
    this.detalle = item;
  }

  cerrarDetalle(): void {
    this.detalle = null;
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
