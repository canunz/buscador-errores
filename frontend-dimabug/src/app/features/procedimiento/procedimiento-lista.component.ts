import { DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { EstadoProcedimiento, Procedimiento } from '../../core/models/procedimiento.model';
import { AuthService } from '../../core/services/auth.service';
import { ProcedimientoService } from '../../core/services/procedimiento.service';
import { AyudaFranjaComponent } from '../../shared/ui/ayuda-franja.component';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';

@Component({
  selector: 'app-procedimiento-lista',
  standalone: true,
  imports: [RouterLink, FormsModule, DatePipe, LoadingModalComponent, AyudaFranjaComponent],
  templateUrl: './procedimiento-lista.component.html',
  styleUrl: './procedimiento-lista.component.css',
})
export class ProcedimientoListaComponent implements OnInit {
  private readonly procedimientos = inject(ProcedimientoService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly esAdministrador = this.auth.isAdmin;
  readonly puedeGestionar = this.auth.isStaff;
  items: Procedimiento[] = [];
  loading = false;
  error = '';
  publicandoId: number | null = null;
  texto = '';
  estado: EstadoProcedimiento | null = null;

  ngOnInit(): void {
    const guardados = this.procedimientos.listaEnSesion();
    if (guardados.length) {
      this.items = this.visibles(guardados);
    }
    this.cargar();
  }

  get filtrados(): Procedimiento[] {
    const q = this.texto.trim().toLowerCase();
    return this.items.filter((item) => {
      if (this.estado && item.estado !== this.estado) {
        return false;
      }
      if (!q) {
        return true;
      }
      return item.nombre.toLowerCase().includes(q) || (item.descripcion || '').toLowerCase().includes(q);
    });
  }

  contar(estado: EstadoProcedimiento | null): number {
    return estado ? this.items.filter((item) => item.estado === estado).length : this.items.length;
  }

  abrir(item: Procedimiento): void {
    this.procedimientos.prepararGuia(item.id, true);
    void this.router.navigate(['/procedimientos', item.id]);
  }

  publicar(item: Procedimiento, event: Event): void {
    event.stopPropagation();
    if (!this.esAdministrador() || item.estado !== 'BORRADOR' || this.publicandoId != null) {
      return;
    }
    this.publicandoId = item.id;
    this.error = '';
    this.procedimientos.cambiarEstado(item.id, 'PUBLICADO').subscribe({
      next: (actualizado) => {
        this.items = this.items.map((actual) => (actual.id === actualizado.id ? actualizado : actual));
        this.publicandoId = null;
      },
      error: (err: Error) => {
        this.publicandoId = null;
        this.error = err.message;
      },
    });
  }

  private visibles(items: Procedimiento[]): Procedimiento[] {
    return this.puedeGestionar() ? items : items.filter((item) => item.estado === 'PUBLICADO');
  }

  private cargar(): void {
    this.loading = this.items.length === 0;
    this.procedimientos.listar().subscribe({
      next: (items) => {
        this.items = this.visibles(items);
        this.loading = false;
        this.items.slice(0, 12).forEach((item) => this.procedimientos.prepararGuia(item.id));
      },
      error: (err: Error) => {
        this.error = err.message;
        this.loading = false;
      },
    });
  }
}
