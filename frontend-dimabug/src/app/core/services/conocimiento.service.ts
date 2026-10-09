import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import {
  Observable,
  catchError,
  concat,
  finalize,
  forkJoin,
  from,
  map,
  mergeMap,
  of,
  scan,
  shareReplay,
  switchMap,
  take,
  tap,
  throwError,
  toArray,
} from 'rxjs';
import { apiUrl } from '../config/api';
import { mensajeApiError } from '../http/api-error';
import { crearFormDataMaterial, descargarMaterialAutenticado } from '../material/material-archivo';
import {
  AsignacionRequest,
  AsignacionSolucion,
  AsociarPruebaRequest,
  BusquedaConocimientoFiltro,
  Conocimiento,
  ConocimientoEstado,
  ConocimientoRequest,
  ResultadoBusquedaConocimiento,
  ItemOrdenado,
  EfectividadSolucion,
  MaterialApoyo,
  MaterialRequest,
  OrdenPruebaRequest,
  OrdenRequest,
  PruebaAsociada,
  RegistrarResultadoRequest,
  ResultadoSolucion,
  Solucion,
  SolucionRequest,
  TipoMaterial,
  TipoSolucion,
} from '../models/conocimiento.model';

export type SolucionCatalogoItem = {
  id: number;
  nombre: string;
  pasos: string;
  anexos: string;
  asignaciones: string[];
  conocimientoId: number;
  conocimientoTitulo: string;
  orden: number;
  tipo: 'PASOS' | 'DERIVACION';
};

@Injectable({ providedIn: 'root' })
export class ConocimientoService {
  private readonly http = inject(HttpClient);
  private readonly base = apiUrl('/conocimientos');
  private readonly listaStorageKey = 'dimabug.conocimientos.lista.v1';
  private readonly solucionesCatalogoKey = 'dimabug.soluciones.catalogo.v1';
  private readonly solucionesOcultasKey = 'dimabug.soluciones.ocultas.v1';

  private listaCache$: Observable<Conocimiento[]> | null = null;
  private detalleCache = new Map<number, Conocimiento>();
  /** Ids eliminados en esta sesión, para que un listado ya en curso no los vuelva a pintar. */
  private readonly retirados = new Set<number>();
  private seccionCache = new Map<string, unknown>();
  private readonly seccionEnVuelo = new Map<string, Observable<unknown>>();
  private readonly seccionMarca = new Map<string, number>();
  private readonly colaGuia: number[] = [];
  private guiaEnCurso = false;
  private solucionesCatalogoCache$: Observable<SolucionCatalogoItem[]> | null = null;

  /** Lectura síncrona de la última lista, para pintar la pantalla sin esperar la red. */
  listaEnSesion(): Conocimiento[] {
    return this.readListaSession() ?? [];
  }

  /** Coincidencias locales sobre la lista ya guardada. No llama al servidor. */
  parecidos(texto: string, limite = 8): Conocimiento[] {
    const palabras = this.palabras(texto);
    if (!palabras.length) {
      return [];
    }
    const consulta = palabras.join(' ');
    return this.listaEnSesion()
      .filter((item) => item.estado === 'PUBLICADO')
      .map((item) => ({ item, puntos: this.puntos(item, palabras, consulta) }))
      .filter((fila) => fila.puntos > 0)
      .sort((a, b) => b.puntos - a.puntos || a.item.titulo.localeCompare(b.item.titulo, 'es'))
      .slice(0, limite)
      .map((fila) => fila.item);
  }

  /** Emite caché de sesión al instante y refresca en segundo plano (sin tocar el backend). */
  listar(force = false): Observable<Conocimiento[]> {
    if (!force && this.listaCache$) {
      return this.listaCache$;
    }

    const stale = !force ? this.readListaSession() : null;
    if (stale?.length) {
      for (const item of stale) {
        this.detalleCache.set(item.id, item);
      }
    }

    const network$ = this.http.get<unknown>(this.base).pipe(
      map((res) => this.sinRetirados(this.asLista(res))),
      tap((items) => {
        this.writeListaSession(items);
        for (const item of items) {
          this.detalleCache.set(item.id, item);
        }
      }),
      catchError((err: HttpErrorResponse) => {
        if (stale?.length) {
          return of(stale);
        }
        this.listaCache$ = null;
        return throwError(() => new Error(mensajeApiError(err, 'No fue posible cargar los conocimientos.')));
      }),
    );

    this.listaCache$ = (stale?.length ? concat(of(stale), network$) : network$).pipe(shareReplay(1));
    return this.listaCache$;
  }

  /**
   * Búsqueda FULLTEXT y filtros en backend.
   * Solo envía los parámetros que tienen valor.
   */
  buscar(filtro: BusquedaConocimientoFiltro): Observable<ResultadoBusquedaConocimiento[]> {
    let params = new HttpParams();
    const texto = filtro.texto?.trim();
    if (texto) {
      params = params.set('texto', texto);
    }
    params = this.paramId(params, 'hardwareId', filtro.hardwareId);
    params = this.paramId(params, 'sistemaId', filtro.sistemaId);
    params = this.paramId(params, 'moduloId', filtro.moduloId);
    params = this.paramId(params, 'frecuenciaId', filtro.frecuenciaId);

    return this.http.get<unknown>(`${this.base}/buscar`, { params }).pipe(
      map((res) => this.asArray(res).map((item) => this.normalizeBusqueda(item))),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No fue posible buscar conocimientos.'))),
      ),
    );
  }

  obtenerPorId(id: number, force = false): Observable<Conocimiento> {
    if (!force && this.detalleCache.has(id)) {
      return of(this.detalleCache.get(id)!);
    }
    return this.http.get<unknown>(`${this.base}/${id}`).pipe(
      map((res) => this.normalize(res)),
      tap((item) => this.detalleCache.set(item.id, item)),
      catchError((err: HttpErrorResponse) => {
        if (err.status === 404) {
          this.retirarDeCache(id);
          return throwError(() => new Error('El conocimiento no está disponible.'));
        }
        return throwError(() => new Error(mensajeApiError(err, 'No se encontró el conocimiento.')));
      }),
    );
  }

  /** Lectura síncrona de la ficha si ya está en memoria o en la lista guardada. */
  peekDetalle(id: number): Conocimiento | null {
    const enMemoria = this.detalleCache.get(id);
    if (enMemoria) {
      return enMemoria;
    }
    const deLista = this.listaEnSesion().find((item) => item.id === id) ?? null;
    if (deLista) {
      this.detalleCache.set(id, deLista);
    }
    return deLista;
  }

  /** Deja la sección escrita al instante, sin esperar otra consulta. */
  recordarSeccion(clave: string, valor: unknown): void {
    this.seccionMarca.set(clave, (this.seccionMarca.get(clave) ?? 0) + 1);
    this.guardarSeccion(clave, valor);
  }

  /** Sección ya guardada (síntomas, causas, pruebas, soluciones o material). */
  leerSeccion<T>(clave: string): T | null {
    if (this.seccionCache.has(clave)) {
      return this.seccionCache.get(clave) as T;
    }
    const guardada = this.leerSeccionLocal<T>(clave);
    if (guardada != null) {
      this.seccionCache.set(clave, guardada);
    }
    return guardada;
  }

  /** Pide la guía completa. `ya` la dispara al abrir; si no, entra a una cola para no saturar. */
  precargarGuia(id: number, ya = false): void {
    if (this.guiaCompleta(id)) {
      return;
    }
    if (ya) {
      this.dispararGuia(id);
      return;
    }
    if (!this.colaGuia.includes(id)) {
      this.colaGuia.push(id);
    }
    this.seguirColaGuia();
  }

  crear(request: ConocimientoRequest): Observable<Conocimiento> {
    return this.http.post<unknown>(this.base, request).pipe(
      map((res) => this.normalize(res)),
      tap((item) => this.invalidateAfterWrite(item)),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No fue posible guardar el conocimiento.'))),
      ),
    );
  }

  modificar(id: number, request: ConocimientoRequest): Observable<Conocimiento> {
    return this.http.put<unknown>(`${this.base}/${id}`, request).pipe(
      map((res) => this.normalize(res)),
      tap((item) => this.invalidateAfterWrite(item)),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No fue posible actualizar el conocimiento.'))),
      ),
    );
  }

  /**
   * Baja lógica. El backend conserva síntomas, causas, pruebas, soluciones,
   * asignaciones y materiales, y responde 204 sin cuerpo.
   */
  eliminar(id: number): Observable<void> {
    return this.http.delete(`${this.base}/${id}`, { responseType: 'text' }).pipe(
      map(() => void 0),
      tap(() => this.retirarDeCache(id)),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensajeEliminar(err)))),
    );
  }

  cambiarEstado(id: number, estado: Exclude<ConocimientoEstado, 'ELIMINADO'>): Observable<Conocimiento> {
    return this.http.patch<unknown>(`${this.base}/${id}/estado`, { estado }).pipe(
      map((res) => this.normalize(res)),
      tap((item) => this.invalidateAfterWrite(item)),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No fue posible cambiar el estado.'))),
      ),
    );
  }

  listarSintomas(conocimientoId: number, force = false): Observable<ItemOrdenado[]> {
    return this.getCached(
      `sintomas:${conocimientoId}`,
      () => this.getOrdenados(`${this.base}/${conocimientoId}/sintomas`, 'No fue posible cargar los síntomas.'),
      force,
    );
  }

  crearSintoma(conocimientoId: number, request: OrdenRequest): Observable<ItemOrdenado> {
    return this.postOrdenado(
      `${this.base}/${conocimientoId}/sintomas`,
      request,
      'No fue posible crear el síntoma.',
    );
  }

  modificarSintoma(conocimientoId: number, sintomaId: number, request: OrdenRequest): Observable<ItemOrdenado> {
    return this.putOrdenado(
      `${this.base}/${conocimientoId}/sintomas/${sintomaId}`,
      request,
      'No fue posible actualizar el síntoma.',
    );
  }

  listarCausas(conocimientoId: number, force = false): Observable<ItemOrdenado[]> {
    return this.getCached(
      `causas:${conocimientoId}`,
      () => this.getOrdenados(`${this.base}/${conocimientoId}/causas`, 'No fue posible cargar las causas.'),
      force,
    );
  }

  crearCausa(conocimientoId: number, request: OrdenRequest): Observable<ItemOrdenado> {
    return this.postOrdenado(
      `${this.base}/${conocimientoId}/causas`,
      request,
      'No fue posible crear la causa.',
    );
  }

  modificarCausa(conocimientoId: number, causaId: number, request: OrdenRequest): Observable<ItemOrdenado> {
    return this.putOrdenado(
      `${this.base}/${conocimientoId}/causas/${causaId}`,
      request,
      'No fue posible actualizar la causa.',
    );
  }

  listarPruebas(conocimientoId: number, force = false): Observable<PruebaAsociada[]> {
    return this.getCached(
      `pruebas:${conocimientoId}`,
      () =>
        this.http.get<unknown>(`${this.base}/${conocimientoId}/pruebas`).pipe(
          map((res) =>
            this.asArray(res)
              .map((item) => this.normalizePrueba(item))
              .sort((a, b) => a.orden - b.orden),
          ),
          catchError((err: HttpErrorResponse) =>
            throwError(() => new Error(mensajeApiError(err, 'No fue posible cargar las pruebas asociadas.'))),
          ),
        ),
      force,
    );
  }

  asociarPrueba(conocimientoId: number, request: AsociarPruebaRequest): Observable<PruebaAsociada> {
    return this.http.post<unknown>(`${this.base}/${conocimientoId}/pruebas`, request).pipe(
      map((res) => this.normalizePrueba(res)),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No fue posible asociar la prueba.'))),
      ),
    );
  }

  modificarPrueba(
    conocimientoId: number,
    pruebaId: number,
    request: OrdenPruebaRequest,
  ): Observable<PruebaAsociada> {
    return this.http.put<unknown>(`${this.base}/${conocimientoId}/pruebas/${pruebaId}`, request).pipe(
      map((res) => this.normalizePrueba(res)),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No fue posible actualizar la prueba asociada.'))),
      ),
    );
  }

  listarSoluciones(conocimientoId: number, force = false): Observable<Solucion[]> {
    return this.getCached(
      `soluciones:${conocimientoId}`,
      () =>
        this.http.get<unknown>(`${this.base}/${conocimientoId}/soluciones`).pipe(
          map((res) =>
            this.asArray(res)
              .map((item) => this.normalizeSolucion(item))
              .sort((a, b) => a.orden - b.orden),
          ),
          catchError((err: HttpErrorResponse) =>
            throwError(() => new Error(mensajeApiError(err, 'No fue posible cargar las soluciones.'))),
          ),
        ),
      force,
    );
  }

  /**
   * Agrega soluciones de todos los conocimientos (no existe /api/soluciones global).
   * Caché de sesión al instante; en frío acumula por lotes sin esperar el total.
   */
  listarSolucionesCatalogo(force = false): Observable<SolucionCatalogoItem[]> {
    if (!force && this.solucionesCatalogoCache$) {
      return this.solucionesCatalogoCache$;
    }

    const stale = !force ? this.readSolucionesSession() : null;

    const mapSoluciones = (c: Conocimiento) =>
      this.listarSoluciones(c.id).pipe(
        map((sols) =>
          sols.map((s) => ({
            id: s.id,
            nombre: s.descripcion,
            pasos: s.tipo === 'DERIVACION' ? 'Derivación' : 'Pasos a seguir',
            anexos: c.titulo,
            asignaciones: [] as string[],
            conocimientoId: c.id,
            conocimientoTitulo: c.titulo,
            orden: s.orden,
            tipo: s.tipo,
          })),
        ),
        catchError(() => of([] as SolucionCatalogoItem[])),
      );

    const fromConocimientos = (conocimientos: Conocimiento[], progressive: boolean) => {
      if (!conocimientos.length) {
        return of([] as SolucionCatalogoItem[]);
      }
      const batches$ = from(conocimientos).pipe(mergeMap((c) => mapSoluciones(c), 4));
      if (progressive) {
        return batches$.pipe(scan((acc, batch) => acc.concat(batch), [] as SolucionCatalogoItem[]));
      }
      return batches$.pipe(
        toArray(),
        map((groups) => groups.flat()),
      );
    };

    const network$ = this.listar().pipe(
      take(1),
      switchMap((conocimientos) => fromConocimientos(conocimientos, !stale?.length)),
      catchError((err: HttpErrorResponse | Error) => {
        if (stale?.length) {
          return of(stale);
        }
        this.solucionesCatalogoCache$ = null;
        const message =
          err instanceof Error ? err.message : mensajeApiError(err, 'No fue posible cargar las soluciones.');
        return throwError(() => new Error(message));
      }),
    );

    this.solucionesCatalogoCache$ = (stale?.length ? concat(of(stale), network$) : network$).pipe(
      map((items) => items.filter((item) => !this.solucionOculta(item))),
      tap((items) => this.writeSolucionesSession(items)),
      shareReplay(1),
    );

    return this.solucionesCatalogoCache$;
  }

  ocultarSolucionCatalogo(conocimientoId: number, id: number): void {
    const clave = `${conocimientoId}-${id}`;
    const ids = this.leerSolucionesOcultas();
    if (!ids.includes(clave)) {
      try {
        sessionStorage.setItem(this.solucionesOcultasKey, JSON.stringify([...ids, clave]));
      } catch {
        // ignore
      }
    }
    this.solucionesCatalogoCache$ = null;
    const actual = this.readSolucionesSession() ?? [];
    this.writeSolucionesSession(actual.filter((item) => `${item.conocimientoId}-${item.id}` !== clave));
  }

  crearSolucion(conocimientoId: number, request: SolucionRequest): Observable<Solucion> {
    return this.http.post<unknown>(`${this.base}/${conocimientoId}/soluciones`, request).pipe(
      map((res) => this.normalizeSolucion(res)),
      tap(() => {
        this.solucionesCatalogoCache$ = null;
        sessionStorage.removeItem(this.solucionesCatalogoKey);
      }),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No fue posible crear la solución.'))),
      ),
    );
  }

  modificarSolucion(
    conocimientoId: number,
    solucionId: number,
    request: SolucionRequest,
  ): Observable<Solucion> {
    return this.http.put<unknown>(`${this.base}/${conocimientoId}/soluciones/${solucionId}`, request).pipe(
      map((res) => this.normalizeSolucion(res)),
      tap(() => {
        this.solucionesCatalogoCache$ = null;
        sessionStorage.removeItem(this.solucionesCatalogoKey);
      }),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No fue posible actualizar la solución.'))),
      ),
    );
  }

  registrarResultado(
    conocimientoId: number,
    solucionId: number,
    request: RegistrarResultadoRequest,
  ): Observable<ResultadoSolucion> {
    const comentario = request.comentario?.trim() ? request.comentario.trim() : null;
    return this.http
      .post<unknown>(`${this.base}/${conocimientoId}/soluciones/${solucionId}/resultados`, {
        funciono: request.funciono,
        comentario,
      })
      .pipe(
        map((res) => this.normalizeResultado(res)),
        catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensajeResultado(err)))),
      );
  }

  obtenerResultados(conocimientoId: number, solucionId: number): Observable<ResultadoSolucion[]> {
    return this.http.get<unknown>(`${this.base}/${conocimientoId}/soluciones/${solucionId}/resultados`).pipe(
      map((res) => this.asArray(res).map((item) => this.normalizeResultado(item))),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensajeResultado(err)))),
    );
  }

  obtenerEfectividad(conocimientoId: number, solucionId: number): Observable<EfectividadSolucion> {
    return this.http.get<unknown>(`${this.base}/${conocimientoId}/soluciones/${solucionId}/efectividad`).pipe(
      map((res) => this.normalizeEfectividad(res, solucionId)),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensajeResultado(err)))),
    );
  }

  listarAsignaciones(conocimientoId: number, solucionId: number, force = false): Observable<AsignacionSolucion[]> {
    return this.getCached(
      `asignaciones:${conocimientoId}:${solucionId}`,
      () =>
        this.http.get<unknown>(`${this.base}/${conocimientoId}/soluciones/${solucionId}/asignaciones`).pipe(
          map((res) => this.asArray(res).map((item) => this.normalizeAsignacion(item))),
          catchError((err: HttpErrorResponse) =>
            throwError(() => new Error(mensajeApiError(err, 'No fue posible cargar las asignaciones.'))),
          ),
        ),
      force,
    );
  }

  crearAsignacion(
    conocimientoId: number,
    solucionId: number,
    request: AsignacionRequest,
  ): Observable<AsignacionSolucion> {
    return this.http
      .post<unknown>(`${this.base}/${conocimientoId}/soluciones/${solucionId}/asignaciones`, request)
      .pipe(
        map((res) => this.normalizeAsignacion(res)),
        catchError((err: HttpErrorResponse) =>
          throwError(() => new Error(mensajeApiError(err, 'No fue posible crear la asignación.'))),
        ),
      );
  }

  modificarAsignacion(
    conocimientoId: number,
    solucionId: number,
    asignacionId: number,
    request: AsignacionRequest,
  ): Observable<AsignacionSolucion> {
    return this.http
      .put<unknown>(
        `${this.base}/${conocimientoId}/soluciones/${solucionId}/asignaciones/${asignacionId}`,
        request,
      )
      .pipe(
        map((res) => this.normalizeAsignacion(res)),
        catchError((err: HttpErrorResponse) =>
          throwError(() => new Error(mensajeApiError(err, 'No fue posible actualizar la asignación.'))),
        ),
      );
  }

  listarMateriales(conocimientoId: number, force = false): Observable<MaterialApoyo[]> {
    return this.getCached(
      `materiales:${conocimientoId}`,
      () =>
        this.http.get<unknown>(`${this.base}/${conocimientoId}/materiales`).pipe(
          map((res) => this.asArray(res).map((item) => this.normalizeMaterial(item))),
          catchError((err: HttpErrorResponse) =>
            throwError(() => new Error(mensajeApiError(err, 'No fue posible cargar el material de apoyo.'))),
          ),
        ),
      force,
    );
  }

  crearMaterial(conocimientoId: number, request: MaterialRequest): Observable<MaterialApoyo> {
    return this.http.post<unknown>(`${this.base}/${conocimientoId}/materiales`, request).pipe(
      map((res) => this.normalizeMaterial(res)),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No fue posible crear el material.'))),
      ),
    );
  }

  crearMaterialArchivo(
    conocimientoId: number,
    nombre: string,
    tipo: TipoMaterial,
    archivo: File,
  ): Observable<MaterialApoyo> {
    return this.http
      .post<unknown>(
        `${this.base}/${conocimientoId}/materiales/archivo`,
        crearFormDataMaterial(nombre, tipo, archivo),
      )
      .pipe(
        map((res) => this.normalizeMaterial(res)),
        catchError((err: HttpErrorResponse) =>
          throwError(() => new Error(mensajeApiError(err, 'No fue posible subir el archivo.'))),
        ),
      );
  }

  descargarMaterial(material: MaterialApoyo): Observable<void> {
    return descargarMaterialAutenticado(this.http, material.url, material.nombre, material.tipo).pipe(
      catchError((err: HttpErrorResponse | Error) =>
        throwError(() =>
          err instanceof HttpErrorResponse
            ? new Error(mensajeApiError(err, 'No fue posible descargar el archivo.'))
            : err,
        ),
      ),
    );
  }

  eliminarMaterial(conocimientoId: number, materialId: number): Observable<void> {
    return this.http.delete(`${this.base}/${conocimientoId}/materiales/${materialId}`, { responseType: 'text' }).pipe(
      map(() => undefined),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No fue posible eliminar el material.'))),
      ),
    );
  }

  modificarMaterial(
    conocimientoId: number,
    materialId: number,
    request: MaterialRequest,
  ): Observable<MaterialApoyo> {
    return this.http.put<unknown>(`${this.base}/${conocimientoId}/materiales/${materialId}`, request).pipe(
      map((res) => this.normalizeMaterial(res)),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No fue posible actualizar el material.'))),
      ),
    );
  }

  private guiaCompleta(id: number): boolean {
    return ['sintomas', 'causas', 'pruebas', 'soluciones', 'materiales'].every(
      (seccion) => this.leerSeccion(`${seccion}:${id}`) != null,
    );
  }

  private dispararGuia(id: number): void {
    if (this.guiaCompleta(id)) {
      return;
    }
    forkJoin([
      this.listarSintomas(id),
      this.listarCausas(id),
      this.listarPruebas(id),
      this.listarSoluciones(id),
      this.listarMateriales(id),
    ])
      .pipe(take(1))
      .subscribe({ error: () => undefined });
  }

  private seguirColaGuia(): void {
    if (this.guiaEnCurso || !this.colaGuia.length) {
      return;
    }
    const id = this.colaGuia.shift();
    if (id == null) {
      return;
    }
    if (this.guiaCompleta(id)) {
      this.seguirColaGuia();
      return;
    }
    this.guiaEnCurso = true;
    forkJoin([
      this.listarSintomas(id),
      this.listarCausas(id),
      this.listarPruebas(id),
      this.listarSoluciones(id),
      this.listarMateriales(id),
    ])
      .pipe(
        take(1),
        finalize(() => {
          this.guiaEnCurso = false;
          this.seguirColaGuia();
        }),
      )
      .subscribe({ error: () => undefined });
  }

  private getCached<T>(key: string, factory: () => Observable<T>, force: boolean): Observable<T> {
    if (!force) {
      const lista = this.leerSeccion<T>(key);
      if (lista != null) {
        this.refrescarSeccion(key, factory);
        return of(lista);
      }
      const enVuelo = this.seccionEnVuelo.get(key);
      if (enVuelo) {
        return enVuelo as Observable<T>;
      }
    }

    const marca = this.seccionMarca.get(key) ?? 0;
    const pedido$ = factory().pipe(
      tap((value) => this.guardarSeccion(key, value, marca)),
      finalize(() => this.seccionEnVuelo.delete(key)),
      shareReplay(1),
    );
    this.seccionEnVuelo.set(key, pedido$);
    return pedido$;
  }

  private refrescarSeccion<T>(key: string, factory: () => Observable<T>): void {
    if (this.seccionEnVuelo.has(key)) {
      return;
    }
    const marca = this.seccionMarca.get(key) ?? 0;
    const pedido$ = factory().pipe(
      tap((value) => this.guardarSeccion(key, value, marca)),
      catchError(() => of(undefined)),
      finalize(() => this.seccionEnVuelo.delete(key)),
      shareReplay(1),
    );
    this.seccionEnVuelo.set(key, pedido$);
    pedido$.subscribe();
  }

  private guardarSeccion(key: string, value: unknown, marca?: number): void {
    if (marca != null && (this.seccionMarca.get(key) ?? 0) !== marca) {
      return;
    }
    this.seccionCache.set(key, value);
    try {
      localStorage.setItem(this.claveSeccion(key), JSON.stringify(value));
    } catch {
      // cuota o modo privado
    }
  }

  private leerSeccionLocal<T>(key: string): T | null {
    try {
      const raw = localStorage.getItem(this.claveSeccion(key)) ?? sessionStorage.getItem(this.claveSeccion(key));
      if (!raw) {
        return null;
      }
      return JSON.parse(raw) as T;
    } catch {
      return null;
    }
  }

  private claveSeccion(key: string): string {
    return `dimabug.seccion.${key}`;
  }

  private clearSeccion(key: string): void {
    this.seccionCache.delete(key);
    this.seccionEnVuelo.delete(key);
    try {
      localStorage.removeItem(this.claveSeccion(key));
      sessionStorage.removeItem(this.claveSeccion(key));
    } catch {
      // ignore
    }
  }

  private mensajeResultado(err: HttpErrorResponse): string {
    if (err.status === 403) {
      return 'No tienes permisos para realizar esta acción.';
    }
    if (err.status === 404) {
      return 'El conocimiento o solución no está disponible.';
    }
    if (err.status === 401) {
      return mensajeApiError(err, 'Su sesión no es válida. Inicie sesión nuevamente.');
    }
    if (err.status === 0 || err.status >= 500) {
      return 'Ocurrió un error inesperado. Intente más tarde.';
    }
    return mensajeApiError(err, 'No fue posible registrar el resultado.');
  }

  private normalizeResultado(raw: unknown): ResultadoSolucion {
    const r = (raw ?? {}) as Record<string, unknown>;
    const usuario = (r['usuario'] ?? {}) as Record<string, unknown>;
    return {
      id: Number(r['id'] ?? 0),
      funciono: r['funciono'] === true,
      comentario: r['comentario'] != null && String(r['comentario']).trim() ? String(r['comentario']) : null,
      fecha: r['fecha'] != null ? String(r['fecha']) : '',
      usuario: {
        id: Number(usuario['id'] ?? 0),
        nombre: String(usuario['nombre'] ?? ''),
        email: String(usuario['email'] ?? ''),
      },
    };
  }

  private normalizeEfectividad(raw: unknown, solucionId: number): EfectividadSolucion {
    const r = (raw ?? {}) as Record<string, unknown>;
    const porcentaje = r['porcentajeEfectividad'];
    return {
      solucionId: Number(r['solucionId'] ?? solucionId),
      totalAplicaciones: Number(r['totalAplicaciones'] ?? 0),
      totalFunciono: Number(r['totalFunciono'] ?? 0),
      totalNoFunciono: Number(r['totalNoFunciono'] ?? 0),
      porcentajeEfectividad: porcentaje == null || porcentaje === '' ? null : Number(porcentaje),
    };
  }

  private mensajeEliminar(err: HttpErrorResponse): string {
    if (err.status === 403) {
      return 'No tienes permisos para eliminar este conocimiento.';
    }
    if (err.status === 404) {
      return 'El conocimiento no está disponible.';
    }
    if (err.status === 401) {
      return mensajeApiError(err, 'Su sesión no es válida. Inicie sesión nuevamente.');
    }
    if (err.status === 0 || err.status >= 500) {
      return 'Ocurrió un error inesperado. Intente más tarde.';
    }
    return mensajeApiError(err, 'No fue posible eliminar el conocimiento.');
  }

  private retirarDeCache(id: number): void {
    this.retirados.add(id);
    this.listaCache$ = null;
    this.solucionesCatalogoCache$ = null;
    this.detalleCache.delete(id);

    const list = this.readListaSession();
    if (list) {
      this.writeListaSession(list.filter((item) => item.id !== id));
    }

    const soluciones = this.readSolucionesSession();
    if (soluciones) {
      this.writeSolucionesSession(soluciones.filter((item) => item.conocimientoId !== id));
    }

    for (const seccion of ['sintomas', 'causas', 'pruebas', 'soluciones', 'materiales']) {
      this.clearSeccion(`${seccion}:${id}`);
    }
    for (const key of [...this.seccionCache.keys()]) {
      if (key.startsWith(`asignaciones:${id}:`)) {
        this.clearSeccion(key);
      }
    }
    this.limpiarAsignacionesSesion(id);
  }

  private limpiarAsignacionesSesion(id: number): void {
    const prefix = `dimabug.seccion.asignaciones:${id}:`;
    try {
      const keys: string[] = [];
      for (const store of [localStorage, sessionStorage]) {
        for (let i = 0; i < store.length; i += 1) {
          const key = store.key(i);
          if (key?.startsWith(prefix)) {
            keys.push(key);
          }
        }
      }
      for (const key of keys) {
        localStorage.removeItem(key);
        sessionStorage.removeItem(key);
      }
    } catch {
      // ignore
    }
  }

  private sinRetirados(items: Conocimiento[]): Conocimiento[] {
    if (!this.retirados.size) {
      return items;
    }
    return items.filter((item) => !this.retirados.has(item.id));
  }

  private estadoConocimiento(raw: string): ConocimientoEstado {
    if (raw === 'PUBLICADO' || raw === 'ELIMINADO' || raw === 'BORRADOR') {
      return raw;
    }
    return 'BORRADOR';
  }

  private invalidateAfterWrite(item: Conocimiento): void {
    this.listaCache$ = null;
    this.detalleCache.set(item.id, item);
    const list = this.readListaSession() ?? [];
    const idx = list.findIndex((x) => x.id === item.id);
    if (idx >= 0) {
      list[idx] = item;
    } else {
      list.unshift(item);
    }
    this.writeListaSession(list);
    for (const key of [...this.seccionCache.keys()]) {
      if (key.includes(`:${item.id}`) || key.endsWith(`:${item.id}`)) {
        this.seccionCache.delete(key);
      }
    }
  }

  private palabras(texto: string): string[] {
    return this.plano(texto)
      .split(/[^a-z0-9]+/)
      .filter((palabra) => palabra.length >= 2);
  }

  private plano(texto: string): string {
    return texto
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase();
  }

  private puntos(item: Conocimiento, palabras: string[], consulta: string): number {
    const titulo = this.plano(item.titulo);
    const bolsa = this.plano(
      [item.titulo, item.descripcion, item.hardwareNombre, item.sistemaNombre, item.moduloNombre, item.frecuenciaNombre]
        .filter(Boolean)
        .join(' '),
    );
    if (!palabras.every((palabra) => bolsa.includes(palabra))) {
      return 0;
    }
    let puntos = 1;
    if (titulo.includes(consulta)) {
      puntos += 8;
    }
    for (const palabra of palabras) {
      if (titulo.includes(palabra)) {
        puntos += 3;
      }
    }
    return puntos;
  }

  private readListaSession(): Conocimiento[] | null {
    try {
      const raw = sessionStorage.getItem(this.listaStorageKey);
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw) as unknown;
      if (!Array.isArray(parsed)) {
        return null;
      }
      return parsed.map((item) => this.normalize(item));
    } catch {
      return null;
    }
  }

  private writeListaSession(items: Conocimiento[]): void {
    try {
      sessionStorage.setItem(this.listaStorageKey, JSON.stringify(items));
    } catch {
      // quota / private mode: ignore
    }
  }

  private solucionOculta(item: SolucionCatalogoItem): boolean {
    return this.leerSolucionesOcultas().includes(`${item.conocimientoId}-${item.id}`);
  }

  private leerSolucionesOcultas(): string[] {
    try {
      const raw = sessionStorage.getItem(this.solucionesOcultasKey);
      const parsed = raw ? (JSON.parse(raw) as string[]) : [];
      return Array.isArray(parsed) ? parsed.map(String) : [];
    } catch {
      return [];
    }
  }

  private readSolucionesSession(): SolucionCatalogoItem[] | null {
    try {
      const raw = sessionStorage.getItem(this.solucionesCatalogoKey);
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw) as SolucionCatalogoItem[];
      return Array.isArray(parsed) ? parsed : null;
    } catch {
      return null;
    }
  }

  private writeSolucionesSession(items: SolucionCatalogoItem[]): void {
    try {
      sessionStorage.setItem(this.solucionesCatalogoKey, JSON.stringify(items));
    } catch {
      // ignore
    }
  }

  private getOrdenados(url: string, fallback: string): Observable<ItemOrdenado[]> {
    return this.http.get<unknown>(url).pipe(
      map((res) =>
        this.asArray(res)
          .map((item) => this.normalizeOrdenado(item))
          .sort((a, b) => a.orden - b.orden),
      ),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(mensajeApiError(err, fallback)))),
    );
  }

  private postOrdenado(url: string, request: OrdenRequest, fallback: string): Observable<ItemOrdenado> {
    return this.http.post<unknown>(url, request).pipe(
      map((res) => this.normalizeOrdenado(res)),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(mensajeApiError(err, fallback)))),
    );
  }

  private putOrdenado(url: string, request: OrdenRequest, fallback: string): Observable<ItemOrdenado> {
    return this.http.put<unknown>(url, request).pipe(
      map((res) => this.normalizeOrdenado(res)),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(mensajeApiError(err, fallback)))),
    );
  }

  private paramId(params: HttpParams, nombre: string, valor: number | null | undefined): HttpParams {
    if (valor == null || Number.isNaN(Number(valor)) || Number(valor) <= 0) {
      return params;
    }
    return params.set(nombre, String(valor));
  }

  private normalizeBusqueda(raw: unknown): ResultadoBusquedaConocimiento {
    const r = (raw ?? {}) as Record<string, unknown>;
    const id = (valor: unknown): number | null => {
      if (valor == null || valor === '') {
        return null;
      }
      const n = Number(valor);
      return Number.isFinite(n) && n > 0 ? n : null;
    };
    const texto = (valor: unknown): string | null => {
      if (valor == null) {
        return null;
      }
      const s = String(valor).trim();
      return s ? s : null;
    };
    const relevancia = r['relevancia'];
    const relevanciaNum = relevancia == null || relevancia === '' ? null : Number(relevancia);
    return {
      id: Number(r['id'] ?? 0),
      titulo: String(r['titulo'] ?? ''),
      hardwareId: id(r['hardwareId']),
      hardwareNombre: texto(r['hardwareNombre']),
      sistemaId: id(r['sistemaId']),
      sistemaNombre: texto(r['sistemaNombre']),
      moduloId: id(r['moduloId']),
      moduloNombre: texto(r['moduloNombre']),
      frecuenciaId: id(r['frecuenciaId']),
      frecuenciaNombre: texto(r['frecuenciaNombre']),
      relevancia: relevanciaNum != null && Number.isFinite(relevanciaNum) ? relevanciaNum : null,
    };
  }

  private asLista(res: unknown): Conocimiento[] {
    return this.asArray(res).map((item) => this.normalize(item));
  }

  private asArray(res: unknown): unknown[] {
    return Array.isArray(res) ? res : (res as { content?: unknown[] })?.content ?? [];
  }

  private normalize(raw: unknown): Conocimiento {
    const r = (raw ?? {}) as Record<string, unknown>;
    const hardware = this.ref(r['hardware'], r['hardwareId'], r['hardwareNombre']);
    const sistema = this.ref(r['sistema'], r['sistemaId'], r['sistemaNombre']);
    const modulo = this.ref(r['modulo'], r['moduloId'], r['moduloNombre']);
    const frecuencia = this.ref(r['frecuencia'], r['frecuenciaId'], r['frecuenciaNombre']);
    const estadoRaw = String(r['estado'] ?? 'BORRADOR').toUpperCase();
    const creado = r['creadoPor'] ?? r['usuarioCreacion'] ?? r['creador'];

    return {
      id: Number(r['id'] ?? r['conocimientoId'] ?? 0),
      titulo: String(r['titulo'] ?? ''),
      descripcion: String(r['descripcion'] ?? ''),
      comentario: r['comentario'] != null ? String(r['comentario']) : null,
      estado: this.estadoConocimiento(estadoRaw),
      hardwareId: hardware.id,
      sistemaId: sistema.id,
      moduloId: modulo.id,
      frecuenciaId: frecuencia.id,
      hardwareNombre: hardware.nombre,
      sistemaNombre: sistema.nombre,
      moduloNombre: modulo.nombre,
      frecuenciaNombre: frecuencia.nombre,
      creadoPor: this.nombrePersona(creado),
      fechaCreacion: r['fechaCreacion'] != null ? String(r['fechaCreacion']) : null,
      fechaModificacion:
        r['fechaModificacion'] != null
          ? String(r['fechaModificacion'])
          : r['fechaActualizacion'] != null
            ? String(r['fechaActualizacion'])
            : null,
    };
  }

  private normalizeOrdenado(raw: unknown): ItemOrdenado {
    const r = (raw ?? {}) as Record<string, unknown>;
    return {
      id: Number(r['id'] ?? 0),
      descripcion: String(r['descripcion'] ?? ''),
      orden: Number(r['orden'] ?? 0),
    };
  }

  private normalizePrueba(raw: unknown): PruebaAsociada {
    const r = (raw ?? {}) as Record<string, unknown>;
    return {
      id: Number(r['id'] ?? r['pruebaId'] ?? 0),
      descripcion: String(r['descripcion'] ?? ''),
      resultadoEsperado: String(r['resultadoEsperado'] ?? ''),
      orden: Number(r['orden'] ?? 0),
    };
  }

  private normalizeSolucion(raw: unknown): Solucion {
    const r = (raw ?? {}) as Record<string, unknown>;
    const tipo = String(r['tipo'] ?? 'PASOS').toUpperCase() as TipoSolucion;
    return {
      id: Number(r['id'] ?? 0),
      descripcion: String(r['descripcion'] ?? ''),
      tipo: tipo === 'DERIVACION' ? 'DERIVACION' : 'PASOS',
      orden: Number(r['orden'] ?? 0),
    };
  }

  private normalizeAsignacion(raw: unknown): AsignacionSolucion {
    const r = (raw ?? {}) as Record<string, unknown>;
    const depId = Number(r['departamentoId'] ?? 0);
    const respId = Number(r['responsableId'] ?? 0);
    return {
      id: Number(r['id'] ?? 0),
      responsableId: respId > 0 ? respId : null,
      responsableNombre: r['responsableNombre'] != null ? String(r['responsableNombre']) : null,
      departamentoId: depId > 0 ? depId : null,
      departamentoNombre: r['departamentoNombre'] != null ? String(r['departamentoNombre']) : null,
      principal: Boolean(r['principal']),
    };
  }

  private normalizeMaterial(raw: unknown): MaterialApoyo {
    const r = (raw ?? {}) as Record<string, unknown>;
    const tipo = String(r['tipo'] ?? 'ENLACE').toUpperCase() as TipoMaterial;
    const tipos: TipoMaterial[] = ['IMAGEN', 'PDF', 'VIDEO', 'ENLACE'];
    return {
      id: Number(r['id'] ?? 0),
      nombre: String(r['nombre'] ?? ''),
      tipo: tipos.includes(tipo) ? tipo : 'ENLACE',
      url: String(r['url'] ?? ''),
    };
  }

  private ref(
    nested: unknown,
    idRaw: unknown,
    nombreRaw: unknown,
  ): { id: number | null; nombre: string } {
    if (nested && typeof nested === 'object') {
      const n = nested as Record<string, unknown>;
      const id = Number(n['id'] ?? n['hardwareId'] ?? n['sistemaId'] ?? n['moduloId'] ?? n['frecuenciaId'] ?? 0);
      return {
        id: id > 0 ? id : null,
        nombre: String(n['nombre'] ?? n['descripcion'] ?? nombreRaw ?? ''),
      };
    }
    const id = Number(idRaw ?? 0);
    return {
      id: id > 0 ? id : null,
      nombre: nombreRaw != null ? String(nombreRaw) : '',
    };
  }

  private nombrePersona(raw: unknown): string {
    if (!raw) {
      return '';
    }
    if (typeof raw === 'string') {
      return raw;
    }
    if (typeof raw === 'object') {
      const p = raw as Record<string, unknown>;
      return String(p['nombre'] ?? p['usuarioNombre'] ?? p['email'] ?? '');
    }
    return '';
  }
}
