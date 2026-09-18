import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { Conocimiento } from '../../core/models/conocimiento.model';
import { ConocimientoService } from '../../core/services/conocimiento.service';
import { FavoritosService } from '../../core/services/favoritos.service';

@Component({
  selector: 'app-conocimiento',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './conocimiento.component.html',
  styleUrl: './conocimiento.component.css',
})
export class ConocimientoComponent implements OnInit, OnDestroy {
  private readonly conocimientosApi = inject(ConocimientoService);
  private readonly favoritosService = inject(FavoritosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private querySub?: Subscription;

  items: Conocimiento[] = [];
  loading = false;
  refreshing = false;
  error = '';
  q = '';
  sistema = '';
  hardware = '';
  estado = '';
  orden: 'relevante' | 'reciente' = 'relevante';

  get sistemas(): string[] {
    return this.unicos((item) => item.sistemaNombre);
  }

  get hardwares(): string[] {
    return this.unicos((item) => item.hardwareNombre);
  }

  get resultados(): Conocimiento[] {
    const tokens = this.q
      .trim()
      .toLowerCase()
      .split(/\s+/)
      .filter(Boolean);

    const list = this.items.filter((item) => {
      if (
        (this.sistema && item.sistemaNombre !== this.sistema) ||
        (this.hardware && item.hardwareNombre !== this.hardware) ||
        (this.estado && item.estado !== this.estado)
      ) {
        return false;
      }
      if (!tokens.length) {
        return true;
      }
      const texto = [
        item.titulo,
        item.descripcion,
        item.comentario,
        item.sistemaNombre,
        item.hardwareNombre,
        item.moduloNombre,
        item.frecuenciaNombre,
        item.creadoPor,
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();
      return tokens.every((token) => texto.includes(token));
    });

    if (this.orden === 'reciente') {
      return [...list].sort((a, b) =>
        String(b.fechaModificacion || b.fechaCreacion || '').localeCompare(
          String(a.fechaModificacion || a.fechaCreacion || ''),
        ),
      );
    }

    if (!tokens.length) {
      return list;
    }

    return [...list].sort((a, b) => this.relevancia(b, tokens) - this.relevancia(a, tokens));
  }

  ngOnInit(): void {
    this.querySub = this.route.queryParamMap.subscribe((params) => {
      this.q = params.get('q') || '';
    });
    this.cargar();
  }

  ngOnDestroy(): void {
    this.querySub?.unsubscribe();
  }

  /** Mantén la URL sincronizada con la búsqueda del listado. */
  onBuscarChange(): void {
    const q = this.q.trim();
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: q ? { q } : { q: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  cargar(): void {
    this.error = '';
    this.loading = this.items.length === 0;
    this.refreshing = false;
    let emissions = 0;
    this.conocimientosApi.listar().subscribe({
      next: (items) => {
        emissions += 1;
        this.items = items;
        this.loading = false;
        this.refreshing = emissions === 1;
      },
      error: (err: Error) => {
        this.error = err.message;
        this.loading = false;
        this.refreshing = false;
      },
      complete: () => {
        this.refreshing = false;
      },
    });
  }

  abrir(item: Conocimiento): void {
    void this.router.navigate(['/conocimiento', item.id]);
  }

  esFavorito(id: number): boolean {
    return this.favoritosService.tiene(id);
  }

  toggleFavorito(id: number, event: Event): void {
    event.stopPropagation();
    this.favoritosService.toggle(id);
  }

  private relevancia(item: Conocimiento, tokens: string[]): number {
    const titulo = item.titulo.toLowerCase();
    const desc = `${item.descripcion} ${item.sistemaNombre} ${item.hardwareNombre}`.toLowerCase();
    let score = 0;
    for (const token of tokens) {
      if (titulo.startsWith(token)) score += 8;
      else if (titulo.includes(token)) score += 5;
      if (desc.includes(token)) score += 2;
    }
    return score;
  }

  private unicos(pick: (item: Conocimiento) => string): string[] {
    return [...new Set(this.items.map(pick).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'es'));
  }
}
