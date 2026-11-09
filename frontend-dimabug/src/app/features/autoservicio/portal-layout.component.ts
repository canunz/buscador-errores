import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-portal-layout',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <div class="portal">
      <header class="portal-header">
        <a class="marca" routerLink="/autoservicio">
          DimaBug
          <span>Autoservicio</span>
        </a>
        <nav aria-label="Navegación pública">
          <a routerLink="/autoservicio" routerLinkActive="activo" [routerLinkActiveOptions]="{ exact: true }">Buscar soluciones</a>
          <a routerLink="/chatbot" routerLinkActive="activo">Asistente</a>
          @if (conSesion()) {
            <a class="cta sesion" routerLink="/inicio">Ir al inicio{{ nombre() ? ' · ' + nombre() : '' }}</a>
          } @else {
            <a class="cta" routerLink="/login">Iniciar sesión</a>
          }
        </nav>
      </header>
      <main class="portal-main">
        <router-outlet />
      </main>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      min-height: 100vh;
      color: #102044;
      background:
        radial-gradient(circle at 12% 0%, rgba(30, 79, 214, 0.12), transparent 34%),
        radial-gradient(circle at 92% 8%, rgba(124, 92, 240, 0.1), transparent 28%),
        #f4f7fc;
    }
    .portal { min-height: 100vh; }
    .portal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 20px;
      flex-wrap: wrap;
      padding: 18px 6%;
      background: linear-gradient(105deg, #071336 0%, #0b1b4d 46%, #1638a8 100%);
      color: #fff;
      box-shadow: 0 16px 36px rgba(7, 19, 54, 0.2);
    }
    a { color: inherit; text-decoration: none; }
    .marca { font-size: 1.35rem; font-weight: 800; letter-spacing: -0.03em; }
    .marca span {
      display: block;
      margin-top: 2px;
      font-size: 0.78rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: #9eb6f5;
    }
    nav { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
    nav a { padding: 9px 14px; border-radius: 999px; font-size: 0.92rem; font-weight: 650; }
    nav a:hover, nav a.activo { background: rgba(255, 255, 255, 0.12); }
    nav a:focus-visible { outline: 3px solid #7fc5ff; outline-offset: 3px; }
    .cta { background: #fff; color: #0b1b4d; font-weight: 800; }
    .cta.sesion {
      background: rgba(255, 255, 255, 0.16);
      color: #fff;
      border: 1px solid rgba(255, 255, 255, 0.28);
    }
    .portal-main {
      width: min(1120px, calc(100% - 32px));
      margin: 0 auto;
      padding: 28px 0 64px;
    }
    @media (max-width: 600px) {
      .portal-header { padding: 16px 18px; }
      nav { width: 100%; }
    }
  `],
})
export class PortalLayoutComponent {
  private readonly auth = inject(AuthService);

  readonly conSesion = computed(() => {
    const valor = this.auth.isAuthenticated as unknown;
    return typeof valor === 'function' ? !!(valor as () => boolean)() : false;
  });

  readonly nombre = computed(() => {
    const leer = this.auth.usuario as unknown;
    const usuario = typeof leer === 'function' ? (leer as () => { usuarioNombre?: string } | null)() : null;
    return usuario?.usuarioNombre?.split(' ')[0] ?? '';
  });
}
