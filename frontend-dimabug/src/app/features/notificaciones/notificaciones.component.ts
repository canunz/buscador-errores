import { DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Aviso, NotificacionService } from '../../core/services/notificacion.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-notificaciones',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './notificaciones.component.html',
  styleUrl: './notificaciones.component.css',
})
export class NotificacionesComponent implements OnInit {
  private readonly notificaciones = inject(NotificacionService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  avisos: Aviso[] = this.notificaciones.propiasAhora();
  vista: 'nuevas' | 'leidas' = 'nuevas';
  private leidas = new Set<string>(this.leidasGuardadas());

  ngOnInit(): void {
    this.notificaciones.propias().subscribe((avisos) => {
      this.avisos = avisos;
    });
  }

  sinLeer(): number {
    return this.avisos.filter((aviso) => !this.leidas.has(aviso.id)).length;
  }

  yaLeidas(): number {
    return this.avisos.length - this.sinLeer();
  }

  visibles(): Aviso[] {
    return this.avisos.filter((aviso) => (this.vista === 'leidas' ? this.leida(aviso) : !this.leida(aviso)));
  }

  leida(aviso: Aviso): boolean {
    return this.leidas.has(aviso.id);
  }

  abrir(aviso: Aviso): void {
    void this.router.navigateByUrl(aviso.ruta);
  }

  marcarUna(aviso: Aviso): void {
    this.leidas.add(aviso.id);
    this.guardarLeidas();
  }

  desmarcarUna(aviso: Aviso): void {
    this.leidas.delete(aviso.id);
    this.guardarLeidas();
  }

  marcarTodas(): void {
    this.avisos.forEach((aviso) => this.leidas.add(aviso.id));
    this.guardarLeidas();
    this.vista = 'leidas';
  }

  private clave(): string {
    return `dimabug.notif.leidas.${this.auth.usuario()?.usuarioId ?? 0}`;
  }

  private leidasGuardadas(): string[] {
    try {
      const raw = localStorage.getItem(this.clave());
      const parsed = raw ? (JSON.parse(raw) as unknown) : [];
      return Array.isArray(parsed) ? parsed.map(String) : [];
    } catch {
      return [];
    }
  }

  private guardarLeidas(): void {
    localStorage.setItem(this.clave(), JSON.stringify([...this.leidas]));
  }
}
