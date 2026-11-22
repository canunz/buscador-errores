import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Componente raíz de la aplicación. Solo contiene el router-outlet donde se cargan las pantallas.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: '<router-outlet />',
  styles: [
    `
      :host {
        display: block;
        min-height: 100vh;
      }
    `,
  ],
})
export class AppComponent {}
