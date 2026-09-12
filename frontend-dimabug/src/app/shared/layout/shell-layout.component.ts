import { Component, HostListener, computed, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
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

  readonly usuario = this.auth.usuario;
  readonly isAdmin = computed(() => esAdministrador(this.usuario()));
  readonly iniciales = computed(() => iniciales(this.usuario()?.usuarioNombre));
  readonly rolNombre = computed(
    () => this.usuario()?.rol?.rolNombre || this.usuario()?.rolNombre || 'Usuario',
  );
  readonly usuarioLogin = computed(() => loginUsername(this.usuario()));

  fadeIn = false;
  menuOpen = false;
  usuarioMenuOpen = false;
  temaOscuro = localStorage.getItem('dimabug-tema') === 'oscuro';

  constructor() {
    this.applyTema();
    this.fadeIn = !this.esInicio(this.router.url);
    this.router.events.pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd)).subscribe((event) => {
      if (this.esInicio(event.urlAfterRedirects)) {
        this.fadeIn = false;
        return;
      }
      this.fadeIn = false;
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          this.fadeIn = true;
        });
      });
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

  @HostListener('document:click')
  closeUsuarioMenu(): void {
    this.usuarioMenuOpen = false;
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
