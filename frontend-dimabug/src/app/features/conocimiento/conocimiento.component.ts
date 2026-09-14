import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
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
export class ConocimientoComponent implements OnInit {
  private readonly conocimientosApi = inject(ConocimientoService);
  private readonly favoritosService = inject(FavoritosService);
  private readonly route = inject(ActivatedRoute);

  items: Conocimiento[] = [];
  loading = false;
  error = '';
  q = this.route.snapshot.queryParamMap.get('q') || '';
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

  get chips(): { key: 'sistema' | 'hardware' | 'estado'; label: string; value: string }[] {
    const chips: { key: 'sistema' | 'hardware' | 'estado'; label: string; value: string }[] = [];
    if (this.sistema) chips.push({ key: 'sistema', label: 'Sistema', value: this.sistema });
    if (this.hardware) chips.push({ key: 'hardware', label: 'Hardware', value: this.hardware });
    if (this.estado) chips.push({ key: 'estado', label: 'Estado', value: this.estado });
    return chips;
  }

  get resultados(): Conocimiento[] {
    const term = this.q.trim().toLowerCase();
    const list = this.items.filter((item) => {
      const texto = `${item.titulo} ${item.descripcion} ${item.sistemaNombre} ${item.hardwareNombre}`.toLowerCase();
      return (
        (!term || texto.includes(term)) &&
        (!this.sistema || item.sistemaNombre === this.sistema) &&
        (!this.hardware || item.hardwareNombre === this.hardware) &&
        (!this.estado || item.estado === this.estado)
      );
    });
    if (this.orden === 'reciente') {
      return [...list].sort((a, b) =>
        String(b.fechaModificacion || b.fechaCreacion || '').localeCompare(
          String(a.fechaModificacion || a.fechaCreacion || ''),
        ),
      );
    }
    return list;
  }

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.loading = true;
    this.error = '';
    this.conocimientosApi.listar().subscribe({
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

  limpiarFiltros(): void {
    this.sistema = '';
    this.hardware = '';
    this.estado = '';
  }

  quitar(key: 'sistema' | 'hardware' | 'estado'): void {
    this[key] = '';
  }

  esFavorito(id: number): boolean {
    return this.favoritosService.tiene(id);
  }

  toggleFavorito(id: number, event: Event): void {
    event.stopPropagation();
    this.favoritosService.toggle(id);
  }

  private unicos(pick: (item: Conocimiento) => string): string[] {
    return [...new Set(this.items.map(pick).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'es'));
  }
}
