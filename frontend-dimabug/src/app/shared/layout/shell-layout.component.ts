import { Component, DestroyRef, HostListener, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Observable, catchError, concat, filter, interval, of } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { Aviso, NotificacionService } from '../../core/services/notificacion.service';
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
  avisos: Aviso[] = [];
  leidas = new Set<string>();
  avisosAbiertos = false;
  private ultimaRevision = Date.now();
  temaOscuro = localStorage.getItem('dimabug-tema') === 'oscuro';

  constructor() {
    this.applyTema();
    // El resto de pantallas se precarga después, de a una, para no frenar la que estás viendo.
    setTimeout(() => this.precargarEnSegundoPlano(), 8000);
    setTimeout(() => this.cargarConteo(), 8000);
    // Revisa cada minuto si hay tickets nuevos o cambios para la campana.
    interval(60_000)
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => this.cargarConteo());
    this.fadeIn = !this.esInicio(this.router.url);
    this.router.events.pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd)).subscribe((event) => {
      this.fadeIn = !this.esInicio(event.urlAfterRedirects);
      this.avisosAbiertos = false;
      // Al cambiar de pantalla revisa novedades, sin repetir consultas seguidas.
      if (Date.now() - this.ultimaRevision > 45_000) {
        this.cargarConteo();
      }
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

  alternarAvisos(): void {
    this.usuarioMenuOpen = false;
    this.avisosAbiertos = !this.avisosAbiertos;
    if (this.avisosAbiertos) {
      this.cargarConteo();
    }
  }

  abrirAviso(aviso: Aviso): void {
    this.leidas.add(aviso.id);
    this.guardarLeidas();
    this.avisosAbiertos = false;
    if (this.router.url.split('?')[0] === aviso.ruta) {
      // Ya estás en esa pantalla: se actualiza con lo último en vez de quedar igual.
      window.dispatchEvent(new Event('focus'));
      return;
    }
    this.router.navigateByUrl(aviso.ruta);
  }

  marcarTodasLeidas(): void {
    this.avisos.forEach((aviso) => this.leidas.add(aviso.id));
    this.guardarLeidas();
  }

  private guardarLeidas(): void {
    const id = this.usuario()?.usuarioId ?? 0;
    try {
      localStorage.setItem(`dimabug.notif.leidas.${id}`, JSON.stringify([...this.leidas]));
    } catch {
      // Si no se puede guardar, igual se actualiza el contador en pantalla.
    }
    this.sinLeer = this.avisos.filter((aviso) => !this.leidas.has(aviso.id)).length;
  }

  @HostListener('document:click')
  closeUsuarioMenu(): void {
    this.usuarioMenuOpen = false;
    this.avisosAbiertos = false;
  }

  @HostListener('window:focus')
  alVolver(): void {
    this.cargarConteo();
  }

  private precargarEnSegundoPlano(): void {
    const enTickets = this.router.url.includes('/tickets');
    const tareas: Observable<unknown>[] = [
      this.procedimientos.listar(),
      this.conocimientos.listar(),
      this.ejecuciones.listar(),
    ];
    if (!enTickets) {
      tareas.push(this.clasificacion.listarHardwareDetalle(), this.organizacion.listarDepartamentosDetalle());
    }
    if (this.isAdmin()) {
      tareas.push(this.usuariosApi.listar(), this.usuariosApi.listarRoles());
    }
    if (this.isStaff()) {
      tareas.push(this.inicio.dashboard());
    }
    concat(...tareas.map((tarea) => tarea.pipe(catchError(() => of(null))))).subscribe();
  }

  private cargarConteo(): void {
    this.ultimaRevision = Date.now();
    const id = this.usuario()?.usuarioId ?? 0;
    let leidas = new Set<string>();
    try {
      const raw = localStorage.getItem(`dimabug.notif.leidas.${id}`);
      const parsed = raw ? (JSON.parse(raw) as unknown) : [];
      leidas = new Set(Array.isArray(parsed) ? parsed.map(String) : []);
    } catch {
      leidas = new Set();
    }
    this.leidas = leidas;
    this.notificaciones.propias().subscribe((avisos) => {
      this.avisos = avisos;
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
