import { Injectable, inject } from '@angular/core';
import { Observable, catchError, concat, forkJoin, map, of, tap } from 'rxjs';
import { Conocimiento } from '../models/conocimiento.model';
import { Ejecucion } from '../models/ejecucion.model';
import { Procedimiento } from '../models/procedimiento.model';
import { EstadoEjecucion } from '../models/ejecucion.model';
import { esStaff } from '../models/usuario.model';
import { AuthService } from './auth.service';
import { ConocimientoService } from './conocimiento.service';
import { EjecucionService } from './ejecucion.service';
import { ProcedimientoService } from './procedimiento.service';

export interface Aviso {
  id: string;
  titulo: string;
  detalle: string;
  fecha: string | null;
  ruta: string;
}

@Injectable({ providedIn: 'root' })
export class NotificacionService {
  private readonly auth = inject(AuthService);
  private readonly ejecuciones = inject(EjecucionService);
  private readonly conocimientos = inject(ConocimientoService);
  private readonly procedimientos = inject(ProcedimientoService);

  propiasAhora(): Aviso[] {
    const ejecuciones = this.ejecuciones.listaEnSesion();
    const conocimientos = this.conocimientos.listaEnSesion();
    const procedimientos = this.procedimientos.listaEnSesion();
    if (!ejecuciones.length && !conocimientos.length && !procedimientos.length) {
      return this.leerCopia();
    }
    const avisos = this.armar(ejecuciones, conocimientos, procedimientos);
    this.guardarCopia(avisos);
    return avisos;
  }

  propias(): Observable<Aviso[]> {
    const ahora = this.propiasAhora();
    const red = forkJoin({
      ejecuciones: this.ejecuciones.listar().pipe(catchError(() => of(this.ejecuciones.listaEnSesion()))),
      conocimientos: this.conocimientos.listar().pipe(catchError(() => of(this.conocimientos.listaEnSesion()))),
      procedimientos: this.procedimientos.listar().pipe(catchError(() => of(this.procedimientos.listaEnSesion()))),
    }).pipe(
      map(({ ejecuciones, conocimientos, procedimientos }) => this.armar(ejecuciones, conocimientos, procedimientos)),
      tap((avisos) => this.guardarCopia(avisos)),
      catchError(() => of(ahora)),
    );
    return concat(of(ahora), red);
  }

  private claveCopia(): string {
    return `dimabug.notif.avisos.${this.auth.usuario()?.usuarioId ?? 0}`;
  }

  private guardarCopia(avisos: Aviso[]): void {
    const id = this.auth.usuario()?.usuarioId ?? 0;
    if (!id) {
      return;
    }
    try {
      localStorage.setItem(this.claveCopia(), JSON.stringify(avisos));
    } catch {
      // La bandeja sigue en memoria si el navegador no permite guardar.
    }
  }

  private leerCopia(): Aviso[] {
    try {
      const raw = localStorage.getItem(this.claveCopia());
      const parsed = raw ? (JSON.parse(raw) as Aviso[]) : [];
      return Array.isArray(parsed) ? parsed.filter((aviso) => !!aviso?.id && !!aviso.titulo) : [];
    } catch {
      return [];
    }
  }

  private armar(ejecuciones: Ejecucion[], conocimientos: Conocimiento[], procedimientos: Procedimiento[]): Aviso[] {
    const yo = this.auth.usuario();
    if (!yo || !esStaff(yo)) {
      return [];
    }
    const correo = (yo.usuarioEmail || '').trim().toLowerCase();
    const nombre = (yo.usuarioNombre || '').trim().toLowerCase();
    const id = yo.usuarioId;
    const nombres = new Map(procedimientos.map((item) => [item.id, item.nombre]));
    const avisos: Aviso[] = [];
    for (const ejecucion of ejecuciones) {
      const suya =
        (id > 0 && ejecucion.usuario?.id === id) ||
        (!!correo && (ejecucion.usuario?.email || '').trim().toLowerCase() === correo);
      if (!suya) {
        continue;
      }
      avisos.push({
        id: `ejecucion-${ejecucion.id}`,
        titulo: this.tituloEjecucion(ejecucion.estado),
        detalle: nombres.get(ejecucion.procedimientoId) || 'Procedimiento',
        fecha: ejecucion.fechaFin || ejecucion.fechaInicio,
        ruta: `/ejecuciones/${ejecucion.id}`,
      });
    }
    for (const caso of conocimientos) {
      if (caso.estado !== 'BORRADOR') {
        continue;
      }
      const autor = (caso.creadoPor || '').trim().toLowerCase();
      if (!autor || (autor !== nombre && autor !== correo)) {
        continue;
      }
      avisos.push({
        id: `borrador-${caso.id}`,
        titulo: 'Tu caso sigue en borrador',
        detalle: caso.titulo,
        fecha: caso.fechaModificacion || caso.fechaCreacion,
        ruta: `/conocimiento/${caso.id}`,
      });
    }
    return avisos.sort((a, b) => (b.fecha || '').localeCompare(a.fecha || ''));
  }

  private tituloEjecucion(estado: EstadoEjecucion): string {
    if (estado === 'COMPLETADA') {
      return 'Completaste una ejecución';
    }
    if (estado === 'CANCELADA') {
      return 'Cancelaste una ejecución';
    }
    return 'Tu ejecución sigue abierta';
  }
}
