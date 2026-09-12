import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CatalogoService } from '../../core/services/catalogo.service';
import { FavoritosService } from '../../core/services/favoritos.service';
import { ConocimientoBusqueda } from '../../core/models/catalogo.model';

@Component({
  selector: 'app-conocimiento',
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink],
  templateUrl: './conocimiento.component.html',
  styleUrl: './conocimiento.component.css',
})
export class ConocimientoComponent {
  private readonly catalogo = inject(CatalogoService);
  private readonly favoritosService = inject(FavoritosService);
  private readonly route = inject(ActivatedRoute);

  q = this.route.snapshot.queryParamMap.get('q') || '';
  sistema = '';
  hardware = '';
  categoria = '';
  tipo = '';
  orden: 'relevante' | 'reciente' = 'relevante';

  get sistemas(): string[] {
    return this.unicos((item) => item.sistema);
  }

  get hardwares(): string[] {
    return this.unicos((item) => item.hardware);
  }

  get categorias(): string[] {
    return this.unicos((item) => item.categoria);
  }

  get chips(): { key: 'sistema' | 'hardware' | 'categoria' | 'tipo'; label: string; value: string }[] {
    const chips: { key: 'sistema' | 'hardware' | 'categoria' | 'tipo'; label: string; value: string }[] = [];
    if (this.sistema) chips.push({ key: 'sistema', label: 'Sistema', value: this.sistema });
    if (this.hardware) chips.push({ key: 'hardware', label: 'Hardware', value: this.hardware });
    if (this.categoria) chips.push({ key: 'categoria', label: 'Categoría', value: this.categoria });
    if (this.tipo) chips.push({ key: 'tipo', label: 'Tipo', value: this.tipo });
    return chips;
  }

  get resultados(): ConocimientoBusqueda[] {
    const term = this.q.trim().toLowerCase();
    const list = this.catalogo.conocimientos().filter((item) => {
      const texto = `${item.titulo} ${item.descripcion} ${item.sistema} ${item.hardware} ${item.categoria}`.toLowerCase();
      const matchQ = !term || texto.includes(term);
      return (
        matchQ &&
        (!this.sistema || item.sistema === this.sistema) &&
        (!this.hardware || item.hardware === this.hardware) &&
        (!this.categoria || item.categoria === this.categoria) &&
        (!this.tipo || item.tipo === this.tipo)
      );
    });
    if (this.orden === 'reciente') {
      return [...list].sort((a, b) => b.fecha.localeCompare(a.fecha));
    }
    return list;
  }

  limpiarFiltros(): void {
    this.sistema = '';
    this.hardware = '';
    this.categoria = '';
    this.tipo = '';
  }

  quitar(key: 'sistema' | 'hardware' | 'categoria' | 'tipo'): void {
    this[key] = '';
  }

  esFavorito(id: number): boolean {
    return this.favoritosService.tiene(id);
  }

  toggleFavorito(id: number, event: Event): void {
    event.stopPropagation();
    this.favoritosService.toggle(id);
  }

  private unicos(pick: (item: ConocimientoBusqueda) => string): string[] {
    return [...new Set(this.catalogo.conocimientos().map(pick))].sort((a, b) => a.localeCompare(b, 'es'));
  }
}
