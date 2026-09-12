import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-ejecuciones',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="cat-page">
      <div class="franja">
        <div>
          <p>Operaciones</p>
          <h1>Mis ejecuciones</h1>
          <span>Historial de procedimientos que has iniciado o completado.</span>
        </div>
        <a routerLink="/procedimientos" class="btn-crear">Ver procedimientos</a>
      </div>

      <div class="tabla-wrap">
        <table>
          <thead>
            <tr>
              <th class="col-acciones">Acciones</th>
              <th>Procedimiento</th>
              <th>Estado</th>
              <th>Inicio</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>
                <div class="acciones">
                  <a class="act ver" routerLink="/procedimientos">Abrir</a>
                </div>
              </td>
              <td><strong>Activar plan B en sucursal</strong></td>
              <td><span class="estado activo">Completada</span></td>
              <td>05/02/2026 09:14</td>
            </tr>
            <tr>
              <td>
                <div class="acciones">
                  <a class="act ver" routerLink="/procedimientos">Abrir</a>
                </div>
              </td>
              <td><strong>Reinstalar impresora</strong></td>
              <td><span class="estado warn">En curso</span></td>
              <td>12/02/2026 18:02</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  `,
  styleUrl: '../../shared/ui/catalogo-page.css',
})
export class EjecucionesComponent {}
