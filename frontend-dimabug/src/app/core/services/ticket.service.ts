import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, catchError, concat, finalize, forkJoin, map, of, shareReplay, switchMap, tap, throwError } from 'rxjs';
import { apiUrl } from '../config/api';
import { AuthService } from './auth.service';
import { mensajeApiError } from '../http/api-error';
import {
  ConocimientoSugerido,
  ConteoTickets,
  CrearTicketRequest,
  EstadoTicket,
  FiltroTickets,
  PaginaTickets,
  PrioridadTicket,
  TicketDetalle,
  TicketResumen,
} from '../models/ticket.model';

/** Convierte LocalDateTime (texto ISO o arreglo [a,m,d,h,mi,s]) a texto ISO. */
export function fechaIso(valor: unknown): string {
  if (valor == null || valor === '') {
    return '';
  }
  if (Array.isArray(valor)) {
    const [anio, mes, dia, hora = 0, minuto = 0, segundo = 0] = valor.map((parte) => Number(parte));
    const dos = (n: number) => String(n).padStart(2, '0');
    return `${anio}-${dos(mes)}-${dos(dia)}T${dos(hora)}:${dos(minuto)}:${dos(segundo)}`;
  }
  return String(valor);
}

/** Mensaje claro para el usuario: usa el mensaje del backend en 400, 403 y 409. */
export function mensajeTicketError(err: HttpErrorResponse, fallback: string): string {
  const backend = typeof err.error === 'object' && err.error ? String(err.error.message || '').trim() : '';
  const legible = backend.length > 0 && backend.length <= 160;
  if (err.status === 403) {
    return legible ? backend : 'Tu perfil no tiene permiso para esta acción.';
  }
  if (err.status === 404) {
    return legible ? backend : 'No encontramos lo que buscabas. Puede que no exista o que no tengas acceso.';
  }
  if (err.status === 413) {
    return 'El archivo es demasiado grande. Cada archivo puede pesar hasta 25 MB.';
  }
  return mensajeApiError(err, fallback);
}

@Injectable({ providedIn: 'root' })
export class TicketService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly base = apiUrl('/tickets');

  /**
   * Última respuesta conocida de cada consulta (por usuario). Se muestra al instante
   * y en paralelo se pide la versión actualizada al servidor.
   */
  private readonly ultimas = new Map<string, PaginaTickets>();
  private readonly detalles = new Map<string, TicketDetalle>();
  /** Peticiones en vuelo: si varias pantallas piden lo mismo a la vez, viaja una sola. */
  private readonly enVuelo = new Map<string, Observable<unknown>>();

  private clave(resto: string): string {
    return `${this.auth.usuario()?.usuarioId ?? 0}|${resto}`;
  }

  private compartida<T>(clave: string, crear: () => Observable<T>): Observable<T> {
    let peticion = this.enVuelo.get(clave) as Observable<T> | undefined;
    if (!peticion) {
      peticion = crear().pipe(
        finalize(() => this.enVuelo.delete(clave)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
      this.enVuelo.set(clave, peticion);
    }
    return peticion;
  }

  listar(filtro: FiltroTickets): Observable<PaginaTickets> {
    let params = new HttpParams()
      .set('page', String(filtro.page ?? 0))
      .set('size', String(filtro.size ?? 10));
    if (filtro.estado) params = params.set('estado', filtro.estado);
    if (filtro.prioridad) params = params.set('prioridad', filtro.prioridad);
    if (filtro.etiquetaId) params = params.set('etiquetaId', String(filtro.etiquetaId));
    if (filtro.asignadoId) params = params.set('asignadoId', String(filtro.asignadoId));
    const texto = filtro.texto?.trim();
    if (texto) params = params.set('texto', texto);

    const clave = this.clave(`lista?${params.toString()}`);
    const red = this.compartida(clave, () =>
      this.http.get<PaginaTickets>(this.base, { params }).pipe(
        map((pagina) => ({
          ...pagina,
          contenido: (pagina.contenido ?? []).map((t) => this.normalizarResumen(t)),
        })),
        tap((pagina) => {
          this.ultimas.set(clave, pagina);
          this.escribirSesion(clave, pagina);
        }),
        catchError((err: HttpErrorResponse) =>
          throwError(
            () =>
              new Error(
                err.status === 404
                  ? 'El servidor todavía no tiene activo el módulo de tickets. Hay que reiniciar el backend para que lo cargue.'
                  : mensajeTicketError(err, 'No fue posible cargar los tickets.'),
              ),
          ),
        ),
      ),
    );
    const previa = this.ultimas.get(clave) ?? this.leerSesion<PaginaTickets>(clave);
    if (previa) this.ultimas.set(clave, previa);
    return previa ? concat(of(previa), red) : red;
  }

  /**
   * Todos los contadores en una sola llamada. Muestra al instante el último valor conocido.
   * Si el servidor aún no tiene esta ruta (versión anterior), devuelve null para usar el cálculo antiguo.
   */
  conteo(): Observable<ConteoTickets | null> {
    const clave = this.clave('conteo');
    const red = this.compartida(clave, () =>
      this.http.get<ConteoTickets>(`${this.base}/conteo`).pipe(
        map((c) => (c && c.porEstado ? c : null)),
        tap((c) => c && this.conteos.set(clave, c)),
        catchError((err: HttpErrorResponse) =>
          [400, 404, 405].includes(err.status) ? of(null) : throwError(() => new Error(mensajeTicketError(err, 'No fue posible cargar los contadores.'))),
        ),
      ),
    );
    const previo = this.conteos.get(clave);
    return previo ? concat(of(previo), red) : red;
  }

  private readonly conteos = new Map<string, ConteoTickets>();

  private readonly listas = new Map<string, TicketResumen[]>();

  /**
   * Una sola lista (la del usuario, o todas si es soporte). La comparten bandeja,
   * mis tickets, el inicio y la campana, así no se piden varias veces a la vez.
   */
  todos(): Observable<TicketResumen[]> {
    const clave = this.clave('todos');
    const red = this.compartida(clave, () =>
      this.http.get<PaginaTickets>(this.base, { params: new HttpParams().set('page', '0').set('size', '100') }).pipe(
        map((pagina) => (pagina.contenido ?? []).map((t) => this.normalizarResumen(t))),
        tap((lista) => {
          this.listas.set(clave, lista);
          this.escribirSesion(clave, lista);
        }),
        catchError((err: HttpErrorResponse) => throwError(() => new Error(mensajeTicketError(err, 'No fue posible cargar los tickets.')))),
      ),
    );
    const previa = this.listas.get(clave) ?? this.leerSesion<TicketResumen[]>(clave) ?? undefined;
    if (previa) this.listas.set(clave, previa);
    return previa ? concat(of(previa), red) : red;
  }

  todosEnCache(): TicketResumen[] | null {
    const clave = this.clave('todos');
    return this.listas.get(clave) ?? this.leerSesion<TicketResumen[]>(clave);
  }

  /** Detalle ya visto en esta cuenta, listo para pintarlo sin esperar al servidor. */
  detalleEnCache(id: number): TicketDetalle | null {
    const clave = this.clave(`detalle/${id}`);
    const guardado = this.detalles.get(clave) ?? this.leerSesion<TicketDetalle>(clave);
    if (!guardado?.descripcion) return null;
    this.detalles.set(clave, guardado);
    return guardado;
  }

  /** Solo el total de un estado (para los contadores de la bandeja). */
  contar(estado: EstadoTicket | null): Observable<number> {
    return this.listar({ estado, page: 0, size: 1 }).pipe(map((pagina) => pagina.totalElementos));
  }

  obtener(id: number): Observable<TicketDetalle> {
    const clave = this.clave(`detalle/${id}`);
    const red = this.compartida(clave, () =>
      this.detalle(this.http.get<TicketDetalle>(`${this.base}/${id}`), 'No fue posible cargar el ticket.'),
    );
    const previo = this.detalles.get(clave) ?? this.leerSesion<TicketDetalle>(clave) ?? undefined;
    if (previo) this.detalles.set(clave, previo);
    return previo ? concat(of(previo), red) : red;
  }

  crear(request: CrearTicketRequest): Observable<TicketDetalle> {
    return this.detalle(this.http.post<TicketDetalle>(this.base, request), 'No fue posible crear el ticket.');
  }

  asignar(id: number, asignadoId: number): Observable<TicketDetalle> {
    return this.detalle(
      this.http.patch<TicketDetalle>(`${this.base}/${id}/asignacion`, { asignadoId }),
      'No fue posible asignar el ticket.',
    );
  }

  cambiarPrioridad(id: number, prioridad: PrioridadTicket, motivo?: string | null): Observable<TicketDetalle> {
    return this.detalle(
      this.http.patch<TicketDetalle>(`${this.base}/${id}/prioridad`, { prioridad, motivo: motivo?.trim() || null }),
      'No fue posible cambiar la prioridad.',
    );
  }

  cambiarEstado(id: number, estado: EstadoTicket, motivo?: string | null): Observable<TicketDetalle> {
    return this.detalle(
      this.http.patch<TicketDetalle>(`${this.base}/${id}/estado`, { estado, motivo: motivo?.trim() || null }),
      'No fue posible cambiar el estado.',
    );
  }

  resolver(id: number, solucion: string, conocimientoId: number | null): Observable<TicketDetalle> {
    return this.detalle(
      this.http.patch<TicketDetalle>(`${this.base}/${id}/resolucion`, { solucion: solucion.trim(), conocimientoId }),
      'No fue posible resolver el ticket.',
    );
  }

  crearConocimiento(id: number): Observable<TicketDetalle> {
    return this.detalle(
      this.http.post<TicketDetalle>(`${this.base}/${id}/conocimiento`, {}),
      'No fue posible crear el conocimiento.',
    );
  }

  comentar(id: number, texto: string, interno: boolean): Observable<TicketDetalle> {
    return this.detalle(
      this.http.post<TicketDetalle>(`${this.base}/${id}/comentarios`, { texto: texto.trim(), interno }),
      'No fue posible publicar el comentario.',
    );
  }

  etiquetar(id: number, etiquetaIds: number[]): Observable<TicketDetalle> {
    return this.detalle(
      this.http.put<TicketDetalle>(`${this.base}/${id}/etiquetas`, { etiquetaIds }),
      'No fue posible guardar las etiquetas.',
    );
  }

  /** Sube un archivo al ticket. Con comentarioId queda dentro de ese mensaje. */
  subirAdjunto(id: number, archivo: File, comentarioId?: number | null): Observable<TicketDetalle> {
    const datos = new FormData();
    datos.append('archivo', archivo, archivo.name);
    if (comentarioId) datos.append('comentarioId', String(comentarioId));
    return this.http.post<TicketDetalle | null>(`${this.base}/${id}/adjuntos`, datos).pipe(
      // Si el servidor responde sin el ticket, se pide de nuevo para mostrar el archivo recién subido.
      switchMap((t) =>
        t && t.id
          ? of(this.normalizarDetalle(t))
          : this.detalle(this.http.get<TicketDetalle>(`${this.base}/${id}`), 'No fue posible cargar el ticket.'),
      ),
      tap((t) => this.detalles.set(this.clave(String(t.id)), t)),
      catchError((err: HttpErrorResponse | Error) => {
        if (!(err instanceof HttpErrorResponse)) return throwError(() => err);
        const motivo =
          err.status === 404 || err.status === 405
            ? 'el servidor todavía no tiene activa la subida de archivos (reinicia el backend con Rebuild Project).'
            : err.status === 413
              ? 'pesa más de 25 MB.'
              : err.status >= 500 || err.status === 0
                ? 'el servidor no pudo guardarlo. Revisa que ejecutaste el script de base de datos de adjuntos y reinicia el backend.'
                : mensajeTicketError(err, 'no se pudo subir.');
        return throwError(() => new Error(`No se pudo subir «${archivo.name}»: ${motivo}`));
      }),
    );
  }

  /**
   * Sube varios archivos a la vez (en paralelo, no uno tras otro) y al final
   * trae el ticket una sola vez con todos los archivos.
   */
  subirAdjuntos(id: number, archivos: File[], comentarioId?: number | null): Observable<TicketDetalle | null> {
    if (!archivos.length) return of(null);
    if (archivos.length === 1) return this.subirAdjunto(id, archivos[0], comentarioId);
    return forkJoin(archivos.map((archivo) => this.subirAdjunto(id, archivo, comentarioId))).pipe(
      switchMap(() => this.detalle(this.http.get<TicketDetalle>(`${this.base}/${id}`), 'No fue posible cargar el ticket.')),
      tap((t) => this.detalles.set(this.clave(String(t.id)), t)),
    );
  }

  private readonly archivos = new Map<string, Observable<Blob>>();

  /** Descarga el archivo (con la sesión) para mostrarlo en pantalla. Se guarda en memoria. */
  archivo(ticketId: number, adjuntoId: number): Observable<Blob> {
    const clave = `${ticketId}/${adjuntoId}`;
    let peticion = this.archivos.get(clave);
    if (!peticion) {
      peticion = this.http.get(`${this.base}/${ticketId}/adjuntos/${adjuntoId}`, { responseType: 'blob' }).pipe(
        catchError((err) => {
          this.archivos.delete(clave);
          return throwError(() => err);
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
      this.archivos.set(clave, peticion);
    }
    return peticion;
  }

  sugerencias(texto: string): Observable<ConocimientoSugerido[]> {
    const params = new HttpParams().set('texto', texto.trim());
    return this.http.get<ConocimientoSugerido[]>(`${this.base}/sugerencias`, { params }).pipe(
      map((lista) => lista ?? []),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeTicketError(err, 'No fue posible buscar sugerencias.'))),
      ),
    );
  }

  private detalle(peticion: Observable<TicketDetalle>, fallback: string): Observable<TicketDetalle> {
    return peticion.pipe(
      map((t) => this.normalizarDetalle(t)),
      tap((t) => this.recordarDetalle(t)),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(mensajeTicketError(err, fallback)))),
    );
  }

  private recordarDetalle(ticket: TicketDetalle): void {
    const clave = this.clave(`detalle/${ticket.id}`);
    this.detalles.set(clave, ticket);
    this.escribirSesion(clave, ticket);
    const listaClave = this.clave('todos');
    const lista = this.listas.get(listaClave);
    if (!lista) return;
    const actualizada = lista.some((t) => t.id === ticket.id)
      ? lista.map((t) => (t.id === ticket.id ? { ...t, ...ticket } : t))
      : [ticket, ...lista];
    this.listas.set(listaClave, actualizada);
    this.escribirSesion(listaClave, actualizada);
  }

  private normalizarResumen<T extends TicketResumen>(t: T): T {
    return {
      ...t,
      etiquetas: t.etiquetas ?? [],
      fechaCreacion: fechaIso(t.fechaCreacion),
      fechaActualizacion: fechaIso(t.fechaActualizacion),
    };
  }

  private normalizarDetalle(t: TicketDetalle): TicketDetalle {
    const base = this.normalizarResumen(t);
    return {
      ...base,
      fechaResolucion: t.fechaResolucion ? fechaIso(t.fechaResolucion) : null,
      fechaCierre: t.fechaCierre ? fechaIso(t.fechaCierre) : null,
      comentarios: (t.comentarios ?? []).map((c) => ({ ...c, fecha: fechaIso(c.fecha) })),
      historial: (t.historial ?? []).map((h) => ({ ...h, fecha: fechaIso(h.fecha) })),
      adjuntos: (t.adjuntos ?? []).map((a) => ({
        ...a,
        fecha: fechaIso(a.fecha),
        tamano: Number(a.tamano) || 0,
        tipo: a.tipo ?? (a.mime?.startsWith('image/') ? 'IMAGEN' : a.mime?.startsWith('video/') ? 'VIDEO' : 'DOCUMENTO'),
      })),
    };
  }

  private escribirSesion(clave: string, valor: unknown): void {
    const texto = JSON.stringify(valor);
    try {
      sessionStorage.setItem(`dimabug.tk.${clave}`, texto);
    } catch {
      // Si el navegador no deja guardar, la lista sigue en memoria.
    }
    if (!clave.includes('detalle/')) return;
    try {
      localStorage.setItem(`dimabug.tk.${clave}`, texto);
    } catch {
      // El detalle sigue en esta pestaña aunque no quepa en el almacenamiento local.
    }
  }

  private leerSesion<T>(clave: string): T | null {
    try {
      const raw = sessionStorage.getItem(`dimabug.tk.${clave}`)
        ?? (clave.includes('detalle/') ? localStorage.getItem(`dimabug.tk.${clave}`) : null);
      return raw ? (JSON.parse(raw) as T) : null;
    } catch {
      return null;
    }
  }
}
