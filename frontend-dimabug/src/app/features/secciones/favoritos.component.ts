import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogoService } from '../../core/services/catalogo.service';
import { FavoritosService } from '../../core/services/favoritos.service';

@Component({
  selector: 'app-favoritos',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="cat-page">
      <div class="franja">
        <div>
          <p>Biblioteca</p>
          <h1>Favoritos</h1>
          <span>Accesos rápidos a los conocimientos que marcas con estrella.</span>
        </div>
        <a routerLink="/conocimiento" class="btn-crear">Ir a conocimiento</a>
      </div>

      <div class="tabla-wrap">
        <table>
          <thead>
            <tr>
              <th class="col-acciones">Acciones</th>
              <th>Conocimiento</th>
              <th>Tipo</th>
              <th>Categoría</th>
            </tr>
          </thead>
          <tbody>
            @for (item of items(); track item.id) {
              <tr>
                <td>
                  <div class="acciones">
                    <a class="act ver" [routerLink]="['/conocimiento', item.id]">Ver</a>
                  </div>
                </td>
                <td><strong>{{ item.titulo }}</strong></td>
                <td>{{ item.tipo }}</td>
                <td>{{ item.categoria }}</td>
              </tr>
            } @empty {
              <tr>
                <td colspan="4" class="vacio">Aún no tienes favoritos. Márcalos con la estrella desde Conocimiento.</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styleUrl: '../../shared/ui/catalogo-page.css',
})
export class FavoritosComponent {
  private readonly catalogo = inject(CatalogoService);
  private readonly favoritos = inject(FavoritosService);

  readonly items = computed(() => {
    const ids = this.favoritos.ids();
    return this.catalogo.conocimientos().filter((item) => ids.includes(item.id));
  });
}
