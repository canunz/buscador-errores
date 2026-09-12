import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { CatalogoService } from '../../core/services/catalogo.service';
import { FavoritosService } from '../../core/services/favoritos.service';

@Component({
  selector: 'app-conocimiento-detalle',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './conocimiento-detalle.component.html',
  styleUrl: './conocimiento-detalle.component.css',
})
export class ConocimientoDetalleComponent {
  private readonly catalogo = inject(CatalogoService);
  private readonly favoritosService = inject(FavoritosService);
  private readonly route = inject(ActivatedRoute);
  private readonly id = toSignal(this.route.paramMap.pipe(map((p) => Number(p.get('id')))), {
    initialValue: Number(this.route.snapshot.paramMap.get('id')),
  });

  readonly item = computed(() => this.catalogo.conocimientoPorId(this.id()));
  readonly vecinos = computed(() => this.catalogo.conocimientoVecinos(this.id()));

  readonly favorito = computed(() => {
    const id = this.item()?.id;
    return !!id && this.favoritosService.ids().includes(id);
  });
  utilidad: 'si' | 'no' | null = null;
  copiado = false;

  toggleFavorito(): void {
    const id = this.item()?.id;
    if (id) this.favoritosService.toggle(id);
  }

  marcarUtil(valor: 'si' | 'no'): void {
    this.utilidad = valor;
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
