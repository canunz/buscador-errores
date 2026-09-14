import { DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Conocimiento } from '../../core/models/conocimiento.model';
import { ConocimientoService } from '../../core/services/conocimiento.service';
import { FavoritosService } from '../../core/services/favoritos.service';

@Component({
  selector: 'app-conocimiento-detalle',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './conocimiento-detalle.component.html',
  styleUrl: './conocimiento-detalle.component.css',
})
export class ConocimientoDetalleComponent implements OnInit {
  private readonly conocimientosApi = inject(ConocimientoService);
  private readonly favoritosService = inject(FavoritosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  item: Conocimiento | null = null;
  loading = false;
  error = '';
  publishing = false;
  copiado = false;

  get favorito(): boolean {
    return !!this.item && this.favoritosService.tiene(this.item.id);
  }

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const id = Number(params.get('id'));
      if (id) {
        this.cargar(id);
      }
    });
  }

  cargar(id: number): void {
    this.loading = true;
    this.error = '';
    this.item = null;
    this.conocimientosApi.obtenerPorId(id).subscribe({
      next: (item) => {
        this.item = item;
        this.loading = false;
      },
      error: (err: Error) => {
        this.error = err.message;
        this.loading = false;
      },
    });
  }

  toggleFavorito(): void {
    if (this.item) {
      this.favoritosService.toggle(this.item.id);
    }
  }

  publicar(): void {
    if (!this.item || this.item.estado === 'PUBLICADO') {
      return;
    }
    this.publishing = true;
    this.error = '';
    this.conocimientosApi.cambiarEstado(this.item.id, 'PUBLICADO').subscribe({
      next: (item) => {
        this.item = item;
        this.publishing = false;
      },
      error: (err: Error) => {
        this.error = err.message;
        this.publishing = false;
      },
    });
  }

  editar(): void {
    if (this.item) {
      void this.router.navigate(['/conocimiento', this.item.id, 'editar']);
    }
  }

  compartir(): void {
    const url = window.location.href;
    if (navigator.clipboard?.writeText) {
      void navigator.clipboard.writeText(url);
    }
    this.copiado = true;
    window.setTimeout(() => (this.copiado = false), 1800);
  }
}
