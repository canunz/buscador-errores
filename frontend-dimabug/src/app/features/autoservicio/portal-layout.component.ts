import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { BotonWhatsappComponent } from '../../shared/ui/boton-whatsapp.component';

@Component({
  selector: 'app-portal-layout',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, BotonWhatsappComponent],
  template: `
    <div class="portal" [class.vista-chat]="enChat()">
      <header class="portal-header">
        <div class="portal-barra">
          <a class="marca" routerLink="/autoservicio" aria-label="DimaBug · Autoservicio">
            <img src="assets/img/logo_dimabug.png" alt="DimaBug" width="136" height="64" />
            <span class="marca-texto">
              <strong>Centro de ayuda</strong>
              <small>Soporte TI · Holding Dimarsa</small>
            </span>
          </a>
          <nav aria-label="Navegación pública">
            <a routerLink="/autoservicio" routerLinkActive="activo">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" /><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" /><path d="M9 7h7M9 11h5" /></svg>
              Soluciones
            </a>
            <a routerLink="/chatbot" routerLinkActive="activo">
              <img src="assets/img/asistente_dimabug.png" alt="" width="22" height="22" />
              Asistente
            </a>
            @if (conSesion()) {
              <a class="cta sesion" routerLink="/inicio">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 10.5L12 3l9 7.5" /><path d="M5 9.5V21h14V9.5" /></svg>
                Ir al inicio{{ nombre() ? ' · ' + nombre() : '' }}
              </a>
            } @else {
              <a class="cta" routerLink="/login">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" /><path d="M10 17l5-5-5-5" /><path d="M15 12H3" /></svg>
                Iniciar sesión
              </a>
            }
          </nav>
        </div>
      </header>

      <main class="portal-main">
        <router-outlet />
      </main>

      <footer class="portal-pie">
        <span><strong>DimaBug</strong> · Centro de ayuda de Soporte TI</span>
        <span>Holding Dimarsa</span>
      </footer>

      <!-- Accesos rápidos: asistente y WhatsApp de Soporte. -->
      <app-boton-whatsapp [conAsistente]="true" />
    </div>
  `,
  styles: [`
    :host {
      display: block;
      min-height: 100vh;
      color: #102044;
      background:
        radial-gradient(circle at 10% 0%, rgba(30, 79, 214, 0.08), transparent 32%),
        radial-gradient(circle at 92% 6%, rgba(124, 92, 240, 0.07), transparent 28%),
        #f5f7fb;
    }
    .portal { min-height: 100vh; display: flex; flex-direction: column; }
    a { color: inherit; text-decoration: none; }

    /* Barra superior blanca: el logo se ve con sus colores, sin recuadro. */
    .portal-header {
      position: sticky;
      top: 0;
      z-index: 20;
      background: rgba(255, 255, 255, 0.92);
      backdrop-filter: saturate(160%) blur(10px);
      border-bottom: 1px solid #e3e8f2;
    }
    .portal-barra {
      width: min(1080px, calc(100% - 56px));
      margin: 0 auto;
      min-height: 72px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 16px;
      flex-wrap: wrap;
      padding: 6px 0;
    }
    .marca { display: flex; align-items: center; gap: 14px; min-width: 0; }
    .marca img { display: block; width: 84px; height: 56px; object-fit: contain; }
    .marca-texto {
      display: flex;
      flex-direction: column;
      padding-left: 14px;
      border-left: 1px solid #dfe5f1;
      line-height: 1.2;
    }
    .marca-texto strong { font-size: 1rem; font-weight: 800; color: #0b1b4d; letter-spacing: -0.01em; }
    .marca-texto small { font-size: 0.78rem; color: #64748b; font-weight: 600; }

    nav { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; }
    nav a {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 9px 14px;
      border-radius: 10px;
      font-size: 0.92rem;
      font-weight: 650;
      color: #334155;
      transition: background 0.15s ease, color 0.15s ease;
    }
    nav a:hover { background: #eef2fb; color: #0b1b4d; }
    nav a.activo { color: #1e4fd6; background: #eaf0ff; }
    nav a:focus-visible { outline: 3px solid #6397ef; outline-offset: 3px; }
    nav a svg { width: 18px; height: 18px; flex-shrink: 0; }
    nav a img { display: block; width: 22px; height: 22px; object-fit: contain; }
    nav a.cta { margin-left: 6px; background: #1e4fd6; color: #fff; font-weight: 750; box-shadow: 0 6px 16px rgba(30, 79, 214, 0.25); }
    nav a.cta:hover { background: #1a43b8; color: #fff; }
    nav a.cta.sesion { background: #0b1b4d; box-shadow: none; }

    .portal-main {
      flex: 1;
      width: min(1080px, calc(100% - 56px));
      margin: 0 auto;
      padding: 28px 0 72px;
    }
    .portal.vista-chat .portal-main {
      width: min(1240px, calc(100% - 40px));
      display: flex;
      align-items: center;
      padding: 20px 0 28px;
    }
    .portal.vista-chat .portal-pie {
      width: min(1240px, calc(100% - 40px));
      padding: 12px 0 88px;
    }

    .portal-pie {
      width: min(1080px, calc(100% - 56px));
      margin: 0 auto;
      padding: 18px 0 120px;
      border-top: 1px solid #e3e8f2;
      display: flex;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 8px;
      font-size: 0.85rem;
      color: #64748b;
    }
    .portal-pie strong { color: #0b1b4d; }

    @media (max-width: 600px) {
      .portal-barra, .portal-main, .portal-pie { width: calc(100% - 32px); }
      .portal-barra { padding: 10px 0; }
      .marca img { width: 66px; height: 44px; }
      .marca-texto small { display: none; }
      nav { width: 100%; flex-wrap: nowrap; justify-content: space-between; gap: 2px; }
      nav a { padding: 8px 9px; font-size: 0.84rem; gap: 6px; white-space: nowrap; }
      nav a.cta { margin-left: 0; }
    }
    @media (max-width: 380px) {
      nav a { padding: 8px 7px; }
      nav a.cta svg { display: none; }
    }
  `],
})
export class PortalLayoutComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly enChat = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map(() => this.router.url.startsWith('/chatbot')),
      startWith(this.router.url.startsWith('/chatbot')),
    ),
    { initialValue: this.router.url.startsWith('/chatbot') },
  );

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
