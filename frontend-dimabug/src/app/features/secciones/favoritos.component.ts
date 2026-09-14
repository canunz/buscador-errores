import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Conocimiento } from '../../core/models/conocimiento.model';
import { ConocimientoService } from '../../core/services/conocimiento.service';
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
              <th>Estado</th>
            </tr>
          </thead>
          <tbody>
            @for (item of items; track item.id) {
              <tr>
                <td>
                  <div class="acciones">
                    <a class="act ver" [routerLink]="['/conocimiento', item.id]">Ver</a>
                  </div>
                </td>
                <td><strong>{{ item.titulo }}</strong></td>
                <td>{{ item.estado === 'PUBLICADO' ? 'Publicado' : 'Borrador' }}</td>
              </tr>
            } @empty {
              <tr>
                <td colspan="3" class="vacio">Aún no tienes favoritos. Márcalos con la estrella desde Conocimiento.</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styleUrl: '../../shared/ui/catalogo-page.css',
})
export class FavoritosComponent implements OnInit {
  private readonly conocimientosApi = inject(ConocimientoService);
  private readonly favoritos = inject(FavoritosService);
  items: Conocimiento[] = [];

  ngOnInit(): void {
    this.conocimientosApi.listar().subscribe({
      next: (list) => {
        const ids = this.favoritos.ids();
        this.items = list.filter((item) => ids.includes(item.id));
      },
    });
  }
}
