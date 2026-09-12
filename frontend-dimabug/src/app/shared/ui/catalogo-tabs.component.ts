import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

export interface CatalogoTab {
  label: string;
  ruta: string;
}

@Component({
  selector: 'app-catalogo-tabs',
  standalone: true,
  imports: [RouterLink],
  template: `
    <nav class="tabs" aria-label="Catálogos">
      @for (item of tabs; track item.ruta) {
        <a [routerLink]="item.ruta" [class.on]="activo(item.ruta)">{{ item.label }}</a>
      }
    </nav>
  `,
  styles: `
    .tabs {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin: 0 0 16px;
      padding: 6px;
      background: #fff;
      border: 1px solid #e7e9f0;
      border-radius: 14px;
    }

    a {
      border-radius: 10px;
      padding: 9px 14px;
      color: #475569;
      font-size: 13px;
      font-weight: 650;
      text-decoration: none;
    }

    a.on {
      background: #1e4fd6;
      color: #fff;
    }

    :host-context(.tema-oscuro) .tabs {
      background: #111827;
      border-color: #1f2937;
    }

    :host-context(.tema-oscuro) a {
      color: #cbd5e1;
    }
  `,
})
export class CatalogoTabsComponent {
  private readonly router = inject(Router);

  readonly tabs: CatalogoTab[] = [
    { label: 'Todos', ruta: '/admin' },
    { label: 'Grupos', ruta: '/admin/catalogo/grupos' },
    { label: 'Usuarios', ruta: '/usuarios' },
    { label: 'Departamentos', ruta: '/departamentos' },
    { label: 'Errores', ruta: '/error/nuevo' },
    { label: 'Frecuencias', ruta: '/admin/catalogo/frecuencias' },
    { label: 'Hardware', ruta: '/plataforma/hardware' },
    { label: 'Módulos', ruta: '/admin/catalogo/modulos' },
    { label: 'Pruebas', ruta: '/pruebas' },
    { label: 'Responsables', ruta: '/admin/catalogo/responsables' },
    { label: 'Sistemas', ruta: '/admin/catalogo/sistemas' },
    { label: 'Soluciones', ruta: '/soluciones' },
  ];

  activo(ruta: string): boolean {
    const url = this.router.url.split('?')[0];
    if (ruta === '/admin') {
      return url === '/admin';
    }
    if (ruta === '/pruebas') {
      return url === '/pruebas' || url === '/procedimientos';
    }
    return url === ruta || url.startsWith(`${ruta}/`);
  }
}
