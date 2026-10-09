import { Component, HostListener, computed, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { NotificacionService } from '../../core/services/notificacion.service';
import { ConocimientoService } from '../../core/services/conocimiento.service';
import { EjecucionService } from '../../core/services/ejecucion.service';
import { ClasificacionService } from '../../core/services/clasificacion.service';
import { InicioService } from '../../core/services/inicio.service';
import { OrganizacionService } from '../../core/services/organizacion.service';
import { ProcedimientoService } from '../../core/services/procedimiento.service';
import { UsuarioService } from '../../core/services/usuario.service';
import { esAdministrador, iniciales, loginUsername } from '../../core/models/usuario.model';

@Component({
  selector: 'app-shell-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell-layout.component.html',
  styleUrl: './shell-layout.component.css',
})
export class ShellLayoutComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly procedimientos = inject(ProcedimientoService);
  private readonly conocimientos = inject(ConocimientoService);
  private readonly ejecuciones = inject(EjecucionService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly clasificacion = inject(ClasificacionService);
  private readonly organizacion = inject(OrganizacionService);
  private readonly usuariosApi = inject(UsuarioService);
  private readonly inicio = inject(InicioService);

  readonly usuario = this.auth.usuario;
  readonly isAdmin = computed(() => esAdministrador(this.usuario()));
  readonly isStaff = this.auth.isStaff;
  readonly iniciales = computed(() => iniciales(this.usuario()?.usuarioNombre));
  readonly rolNombre = computed(
    () => this.usuario()?.rol?.rolNombre || this.usuario()?.rolNombre || 'Usuario',
  );
  readonly usuarioLogin = computed(() => loginUsername(this.usuario()));

  fadeIn = false;
  menuOpen = false;
  usuarioMenuOpen = false;
  sinLeer = 0;
  temaOscuro = localStorage.getItem('dimabug-tema') === 'oscuro';

  constructor() {
    this.applyTema();
    this.procedimientos.listar().subscribe({ error: () => undefined });
    this.conocimientos.listar().subscribe({ error: () => undefined });
    this.ejecuciones.listar().subscribe({ error: () => undefined });
    this.clasificacion.listarHardwareDetalle().subscribe({ error: () => undefined });
    this.organizacion.listarDepartamentosDetalle().subscribe({ error: () => undefined });
    if (this.isAdmin()) {
      this.usuariosApi.listar().subscribe({ error: () => undefined });
      this.usuariosApi.listarRoles().subscribe({ error: () => undefined });
    }
    if (this.isStaff()) {
      this.inicio.dashboard().subscribe({ error: () => undefined });
    }
    this.cargarConteo();
    this.fadeIn = !this.esInicio(this.router.url);
    this.router.events.pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd)).subscribe((event) => {
      this.fadeIn = !this.esInicio(event.urlAfterRedirects);
    });
  }

  toggleTema(): void {
    this.temaOscuro = !this.temaOscuro;
    localStorage.setItem('dimabug-tema', this.temaOscuro ? 'oscuro' : 'claro');
    this.applyTema();
  }

  toggleMenu(): void {
    this.menuOpen = !this.menuOpen;
  }

  closeMenu(): void {
    this.menuOpen = false;
  }

  toggleUsuarioMenu(event: Event): void {
    event.stopPropagation();
    this.usuarioMenuOpen = !this.usuarioMenuOpen;
  }

  abrirNotificaciones(event: Event): void {
    event.stopPropagation();
    this.usuarioMenuOpen = false;
    const url = this.router.serializeUrl(this.router.createUrlTree(['/notificaciones']));
    window.open(url, '_blank', 'noopener');
  }

  @HostListener('document:click')
  closeUsuarioMenu(): void {
    this.usuarioMenuOpen = false;
  }

  @HostListener('window:focus')
  alVolver(): void {
    this.cargarConteo();
  }

  private cargarConteo(): void {
    if (!this.isStaff()) {
      this.sinLeer = 0;
      return;
    }
    const id = this.usuario()?.usuarioId ?? 0;
    let leidas = new Set<string>();
    try {
      const raw = localStorage.getItem(`dimabug.notif.leidas.${id}`);
      const parsed = raw ? (JSON.parse(raw) as unknown) : [];
      leidas = new Set(Array.isArray(parsed) ? parsed.map(String) : []);
    } catch {
      leidas = new Set();
    }
    this.notificaciones.propias().subscribe((avisos) => {
      this.sinLeer = avisos.filter((aviso) => !leidas.has(aviso.id)).length;
    });
  }

  logout(): void {
    this.usuarioMenuOpen = false;
    this.auth.logout();
  }

  private applyTema(): void {
    document.body.classList.toggle('tema-oscuro', this.temaOscuro);
  }

  private esInicio(url: string): boolean {
    const path = url.split('?')[0];
    return path === '/inicio' || path === '/' || path.endsWith('/inicio');
  }
}
