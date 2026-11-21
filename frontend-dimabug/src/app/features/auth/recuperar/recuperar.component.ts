import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** La ruta antigua redirige al único flujo de ingreso. */
@Component({
  selector: 'app-recuperar', standalone: true, imports: [RouterLink],
  template: '<p>Ya no necesita contraseña. <a routerLink="/login">Ingresar con un código</a></p>',
})
export class RecuperarComponent {}
