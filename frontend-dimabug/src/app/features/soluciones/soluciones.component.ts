import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ConocimientoService } from '../../core/services/conocimiento.service';
import { SolucionItem } from '../../core/models/catalogo.model';
import { CatalogoTabsComponent } from '../../shared/ui/catalogo-tabs.component';

@Component({
  selector: 'app-soluciones',
  standalone: true,
  imports: [FormsModule, CatalogoTabsComponent, RouterLink],
  templateUrl: './soluciones.component.html',
  styleUrls: ['../../shared/ui/catalogo-page.css', './soluciones.component.css'],
})
export class SolucionesComponent implements OnInit {
  private readonly conocimientos = inject(ConocimientoService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  q = this.route.snapshot.queryParamMap.get('q') || '';
  items: SolucionItem[] = [];
  loading = false;
  error = '';

  get filtrados(): SolucionItem[] {
    const term = this.q.trim().toLowerCase();
    if (!term) {
      return this.items;
    }
    return this.items.filter((item) =>
      [item.nombre, item.pasos, item.anexos, item.asignaciones.join(' '), item.conocimientoTitulo || '']
        .join(' ')
        .toLowerCase()
        .includes(term),
    );
  }

  ngOnInit(): void {
    this.cargar();
  }

  cargar(force = false): void {
    this.loading = !this.items.length;
    this.error = '';
    let first = true;
    this.conocimientos
      .listarSolucionesCatalogo(force)
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

  trackKey(item: SolucionItem): string {
    return `${item.conocimientoId ?? 0}-${item.id}`;
  }

  recortar(texto: string, max = 160): string {
    if (!texto) {
      return '—';
    }
    return texto.length > max ? `${texto.slice(0, max)}…` : texto;
  }

  anexosLista(texto: string): string[] {
    if (!texto?.trim()) {
      return [];
    }
    return [texto.trim()];
  }
}
