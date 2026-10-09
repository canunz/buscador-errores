import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, concat, map, of, shareReplay, tap, throwError } from 'rxjs';
import { apiUrl } from '../config/api';
import { mensajeApiError } from '../http/api-error';
import {
  ActualizarEjecucionPasoRequest,
  CancelarEjecucionRequest,
  Ejecucion,
  EjecucionPaso,
  EstadoEjecucion,
  ProcedimientoResumen,
  EstadoProcedimiento,
} from '../models/ejecucion.model';

@Injectable({ providedIn: 'root' })
export class EjecucionService {
  private readonly http = inject(HttpClient);
  private readonly storageKey = 'dimabug.ejecuciones.lista.v1';
  private listaCache$: Observable<Ejecucion[]> | null = null;

  listaEnSesion(): Ejecucion[] {
    return this.leerLista() ?? [];
  }

  iniciar(procedimientoId: number): Observable<Ejecucion> {
    return this.http.post<unknown>(apiUrl(`/procedimientos/${procedimientoId}/ejecuciones`), null).pipe(
      map((res) => this.normalizeEjecucion(res)),
      tap((item) => this.recordar([item, ...this.listaEnSesion().filter((actual) => actual.id !== item.id)])),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
    );
  }

  listar(force = false): Observable<Ejecucion[]> {
    if (!force && this.listaCache$) {
      return this.listaCache$;
    }
    const stale = !force ? this.leerLista() : null;
    stale?.forEach((item) => this.preparar(item.id));
    const network$ = this.http.get<unknown>(apiUrl('/ejecuciones')).pipe(
      map((res) => this.asArray(res).map((item) => this.normalizeEjecucion(item))),
      tap((items) => {
        this.guardarLista(items);
        items.forEach((item) => {
          this.guardarDetalle(item);
          this.preparar(item.id);
        });
      }),
      catchError((err: HttpErrorResponse) => {
        if (stale?.length) {
          return of(stale);
        }
        this.listaCache$ = null;
        return throwError(() => new Error(this.mensaje(err)));
      }),
    );
    const fuente = stale?.length ? concat(of(stale), network$) : network$;
    this.listaCache$ = fuente.pipe(shareReplay({ bufferSize: 1, refCount: false }));
    return this.listaCache$;
  }

  private readonly detalleCache = new Map<number, Observable<Ejecucion>>();
  private readonly pasosCache = new Map<number, Observable<EjecucionPaso[]>>();
  private readonly pasosMarca = new Map<number, number>();
  private readonly colaPasos: number[] = [];
  private pasosEnCurso = false;

  detalleEnSesion(id: number): Ejecucion | null {
    try {
      const raw = localStorage.getItem(`${this.storageKey}.detalle.${id}`);
      if (!raw) {
        return null;
      }
      return this.normalizeEjecucion(JSON.parse(raw) as unknown);
    } catch {
      return null;
    }
  }

  pasosEnSesion(id: number): EjecucionPaso[] | null {
    try {
      const raw = localStorage.getItem(`${this.storageKey}.pasos.${id}`);
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw) as unknown;
      if (!Array.isArray(parsed)) {
        return null;
      }
      return parsed.map((item) => this.normalizePaso(item));
    } catch {
      return null;
    }
  }

  marcaPasos(id: number): number {
    return this.pasosMarca.get(id) ?? 0;
  }

  fijarPasos(id: number, pasos: EjecucionPaso[]): void {
    this.pasosMarca.set(id, (this.pasosMarca.get(id) ?? 0) + 1);
    this.pasosCache.delete(id);
    this.guardarPasos(id, pasos);
  }

  preparar(id: number, ya = false): void {
    if (ya) {
      this.obtener(id).subscribe({ error: () => undefined });
      this.obtenerPasos(id).subscribe({ error: () => undefined });
      return;
    }
    if (this.pasosEnSesion(id)?.length || this.colaPasos.includes(id)) {
      return;
    }
    this.colaPasos.push(id);
    this.seguirPasos();
  }

  private seguirPasos(): void {
    if (this.pasosEnCurso || !this.colaPasos.length) {
      return;
    }
    const id = this.colaPasos.shift();
    if (id == null) {
      return;
    }
    if (this.pasosEnSesion(id)?.length) {
      this.seguirPasos();
      return;
    }
    this.pasosEnCurso = true;
    this.obtener(id).subscribe({ error: () => undefined });
    this.obtenerPasos(id).subscribe({
      next: () => {
        this.pasosEnCurso = false;
        this.seguirPasos();
      },
      error: () => {
        this.pasosEnCurso = false;
        this.seguirPasos();
      },
    });
  }

  obtener(id: number): Observable<Ejecucion> {
    const actual = this.detalleCache.get(id);
    if (actual) {
      return actual;
    }
    const stale = this.detalleEnSesion(id);
    const network$ = this.http.get<unknown>(apiUrl(`/ejecuciones/${id}`)).pipe(
      map((res) => this.normalizeEjecucion(res)),
      tap((item) => this.guardarDetalle(item)),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
    );
    const fuente = stale ? concat(of(stale), network$) : network$;
    const compartida = fuente.pipe(shareReplay({ bufferSize: 1, refCount: false }));
    this.detalleCache.set(id, compartida);
    return compartida;
  }

  obtenerPasos(id: number): Observable<EjecucionPaso[]> {
    const actual = this.pasosCache.get(id);
    if (actual) {
      return actual;
    }
    const marca = this.marcaPasos(id);
    const stale = this.pasosEnSesion(id);
    const network$ = this.http.get<unknown>(apiUrl(`/ejecuciones/${id}/pasos`)).pipe(
      map((res) => this.asArray(res).map((item) => this.normalizePaso(item))),
      tap((pasos) => {
        if (this.marcaPasos(id) !== marca) {
          return;
        }
        this.guardarPasos(id, pasos);
      }),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
    );
    const fuente = stale?.length ? concat(of(stale), network$) : network$;
    const compartida = fuente.pipe(shareReplay({ bufferSize: 1, refCount: false }));
    this.pasosCache.set(id, compartida);
    return compartida;
  }

  actualizarPaso(
    ejecucionId: number,
    ejecucionPasoId: number,
    request: ActualizarEjecucionPasoRequest,
  ): Observable<EjecucionPaso> {
    const observacion = request.observacion?.trim() ? request.observacion.trim() : null;
    return this.http
      .patch<unknown>(apiUrl(`/ejecuciones/${ejecucionId}/pasos/${ejecucionPasoId}`), {
        cumplido: request.cumplido,
        observacion,
      })
      .pipe(
        map((res) => this.normalizePaso(res)),
        catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
      );
  }

  completar(id: number): Observable<Ejecucion> {
    return this.http.patch<unknown>(apiUrl(`/ejecuciones/${id}/completar`), null).pipe(
      map((res) => this.normalizeEjecucion(res)),
      tap((item) => this.reemplazar(item)),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
    );
  }

  cancelar(id: number, request: CancelarEjecucionRequest): Observable<Ejecucion> {
    const observaciones = request.observaciones?.trim() ? request.observaciones.trim() : null;
    return this.http.patch<unknown>(apiUrl(`/ejecuciones/${id}/cancelar`), { observaciones }).pipe(
      map((res) => this.normalizeEjecucion(res)),
      tap((item) => this.reemplazar(item)),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
    );
  }

  listarProcedimientos(): Observable<ProcedimientoResumen[]> {
    return this.http.get<unknown>(apiUrl('/procedimientos')).pipe(
      map((res) => this.asArray(res).map((item) => this.normalizeProcedimiento(item))),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(mensajeApiError(err, 'No fue posible cargar los procedimientos.')))),
    );
  }

  obtenerProcedimiento(id: number): Observable<ProcedimientoResumen> {
    return this.http.get<unknown>(apiUrl(`/procedimientos/${id}`)).pipe(
      map((res) => this.normalizeProcedimiento(res)),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(mensajeApiError(err, 'El procedimiento no está disponible.')))),
    );
  }

  private reemplazar(item: Ejecucion): void {
    this.guardarDetalle(item);
    this.detalleCache.delete(item.id);
    const actual = this.listaEnSesion();
    if (!actual.some((fila) => fila.id === item.id)) {
      this.recordar([item, ...actual]);
      return;
    }
    this.recordar(actual.map((fila) => (fila.id === item.id ? item : fila)));
  }

  private recordar(items: Ejecucion[]): void {
    this.guardarLista(items);
    this.listaCache$ = null;
  }

  private leerLista(): Ejecucion[] | null {
    try {
      const raw = localStorage.getItem(this.storageKey);
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw) as unknown;
      if (!Array.isArray(parsed)) {
        return null;
      }
      return parsed.map((item) => this.normalizeEjecucion(item));
    } catch {
      return null;
    }
  }

  private guardarLista(items: Ejecucion[]): void {
    try {
      localStorage.setItem(this.storageKey, JSON.stringify(items));
    } catch {
      // cuota o modo privado
    }
  }

  private guardarDetalle(item: Ejecucion): void {
    try {
      localStorage.setItem(`${this.storageKey}.detalle.${item.id}`, JSON.stringify(item));
    } catch {
      // cuota o modo privado
    }
  }

  private guardarPasos(id: number, pasos: EjecucionPaso[]): void {
    try {
      localStorage.setItem(`${this.storageKey}.pasos.${id}`, JSON.stringify(pasos));
    } catch {
      // cuota o modo privado
    }
  }

  private mensaje(err: HttpErrorResponse): string {
    if (err.status === 403) {
      return 'No tienes permisos para acceder o modificar esta ejecución.';
    }
    if (err.status === 404) {
      return 'La ejecución solicitada no está disponible.';
    }
    if (err.status === 401) {
      return mensajeApiError(err, 'Su sesión no es válida. Inicie sesión nuevamente.');
    }
    if (err.status === 0 || err.status >= 500) {
      return 'Ocurrió un error inesperado. Intente más tarde.';
    }
    return mensajeApiError(err, 'No fue posible completar la operación.');
  }

  private asArray(res: unknown): unknown[] {
    return Array.isArray(res) ? res : (res as { content?: unknown[] })?.content ?? [];
  }

  private normalizeEjecucion(raw: unknown): Ejecucion {
    const r = (raw ?? {}) as Record<string, unknown>;
    const usuario = (r['usuario'] ?? {}) as Record<string, unknown>;
    return {
      id: Number(r['id'] ?? 0),
      procedimientoId: Number(r['procedimientoId'] ?? 0),
      estado: this.estado(r['estado']),
      fechaInicio: r['fechaInicio'] != null ? String(r['fechaInicio']) : null,
      fechaFin: r['fechaFin'] != null ? String(r['fechaFin']) : null,
      observaciones: r['observaciones'] != null ? String(r['observaciones']) : null,
      usuario: {
        id: Number(usuario['id'] ?? 0),
        nombre: String(usuario['nombre'] ?? ''),
        email: String(usuario['email'] ?? ''),
      },
    };
  }

  private normalizePaso(raw: unknown): EjecucionPaso {
    const r = (raw ?? {}) as Record<string, unknown>;
    return {
      ejecucionPasoId: Number(r['ejecucionPasoId'] ?? r['id'] ?? 0),
      pasoId: Number(r['pasoId'] ?? 0),
      orden: Number(r['orden'] ?? 0),
      instruccion: String(r['instruccion'] ?? ''),
      esCritico: r['esCritico'] === true,
      cumplido: r['cumplido'] === true,
      observacion: r['observacion'] != null && String(r['observacion']).trim() ? String(r['observacion']) : null,
      fecha: r['fecha'] != null ? String(r['fecha']) : null,
    };
  }

  private normalizeProcedimiento(raw: unknown): ProcedimientoResumen {
    const r = (raw ?? {}) as Record<string, unknown>;
    const estado = String(r['estado'] ?? 'BORRADOR').toUpperCase();
    return {
      id: Number(r['id'] ?? 0),
      nombre: String(r['nombre'] ?? ''),
      descripcion: r['descripcion'] != null ? String(r['descripcion']) : null,
      estado: (estado === 'PUBLICADO' ? 'PUBLICADO' : 'BORRADOR') as EstadoProcedimiento,
    };
  }

  private estado(raw: unknown): EstadoEjecucion {
    const valor = String(raw ?? 'EN_CURSO').toUpperCase();
    if (valor === 'COMPLETADA' || valor === 'CANCELADA') {
      return valor;
    }
    return 'EN_CURSO';
  }
}
