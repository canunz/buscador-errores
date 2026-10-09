import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import {
  Observable,
  catchError,
  concat,
  forkJoin,
  from,
  map,
  mergeMap,
  of,
  scan,
  shareReplay,
  switchMap,
  tap,
  throwError,
} from 'rxjs';
import { apiUrl } from '../config/api';
import { mensajeApiError } from '../http/api-error';
import { DepartamentoContacto, DepartamentoItem, PruebaItem } from '../models/catalogo.model';
import { CatalogoRef, PruebaCatalogo, ResponsableRef } from '../models/conocimiento.model';

@Injectable({ providedIn: 'root' })
export class OrganizacionService {
  private readonly http = inject(HttpClient);
  private readonly departamentosDetalleKey = 'dimabug.departamentos.detalle.v1';
  private readonly pruebasKey = 'dimabug.pruebas.lista.v1';
  private readonly pruebasActivoKey = 'dimabug.pruebas.activo.v1';
  private readonly departamentosOcultosKey = 'dimabug.departamentos.ocultos.v1';
  private readonly pruebasOcultosKey = 'dimabug.pruebas.ocultos.v1';
  private pruebasCache$: Observable<PruebaCatalogo[]> | null = null;
  private departamentosCache$: Observable<CatalogoRef[]> | null = null;
  private departamentosDetalleCache$: Observable<DepartamentoItem[]> | null = null;
  private responsablesCache = new Map<number, ResponsableRef[]>();

  snapshotPruebas(): PruebaCatalogo[] {
    return this.readPruebasSession() ?? [];
  }

  listarPruebas(force = false): Observable<PruebaCatalogo[]> {
    if (!force && this.pruebasCache$) {
      return this.pruebasCache$;
    }

    const stale = !force ? this.readPruebasSession() : null;

    const network$ = this.http.get<unknown>(apiUrl('/pruebas')).pipe(
      map((res) => this.asPruebaLista(res)),
      tap((items) => this.writePruebasSession(items)),
      catchError((err: HttpErrorResponse) => {
        if (stale?.length) {
          return of(stale);
        }
        this.pruebasCache$ = null;
        return throwError(() => new Error(mensajeApiError(err, 'No fue posible cargar el catálogo de pruebas.')));
      }),
    );

    this.pruebasCache$ = (stale?.length ? concat(of(stale), network$) : network$).pipe(shareReplay(1));
    return this.pruebasCache$;
  }

  listarPruebasDetalle(force = false): Observable<PruebaItem[]> {
    return this.listarPruebas(force).pipe(
      map((items) =>
        items
          .filter((p) => !this.estaOculto(this.pruebasOcultosKey, p.id))
          .map((p) => ({
            id: p.id,
            descripcion: p.descripcion,
            resultadoEsperado: p.resultadoEsperado,
            activo: this.activoPrueba(p.id),
          })),
      ),
    );
  }

  listarDepartamentos(force = false): Observable<CatalogoRef[]> {
    if (!force && this.departamentosCache$) {
      return this.departamentosCache$;
    }
    this.departamentosCache$ = this.http.get<unknown>(apiUrl('/departamentos')).pipe(
      map((res) => this.asRefLista(res)),
      catchError((err: HttpErrorResponse) => {
        this.departamentosCache$ = null;
        return throwError(() => new Error(mensajeApiError(err, 'No fue posible cargar los departamentos.')));
      }),
      shareReplay(1),
    );
    return this.departamentosCache$;
  }

  departamentosEnSesion(): DepartamentoItem[] {
    return this.readDepartamentosSession() ?? [];
  }

  listarDepartamentosDetalle(force = false): Observable<DepartamentoItem[]> {
    if (!force && this.departamentosDetalleCache$) {
      return this.departamentosDetalleCache$;
    }

    const stale = !force ? this.readDepartamentosSession() : null;

    const network$ = this.listarDepartamentos(force).pipe(
      switchMap((deps) => {
        const initial: DepartamentoItem[] = deps.map((d) => {
          const prev = stale?.find((s) => s.id === d.id);
          return {
            id: d.id,
            nombre: d.nombre,
            activo: prev?.activo ?? true,
            responsables: prev?.responsables ?? [],
            contactos: prev?.contactos ?? [],
          };
        });

        const faltan = deps.filter((d) => {
          const prev = stale?.find((s) => s.id === d.id);
          return !prev?.responsables.length && !prev?.contactos.length;
        });
        if (!deps.length || !faltan.length) {
          return of(initial);
        }

        const enrich$ = from(faltan).pipe(
          mergeMap(
            (d) =>
              this.detalleDeDepartamento(d).pipe(
                catchError(() =>
                  of({
                    id: d.id,
                    nombre: d.nombre,
                    activo: this.activoDepartamentoGuardado(d.id),
                    responsables: [] as string[],
                    contactos: [] as DepartamentoContacto[],
                  }),
                ),
              ),
            8,
          ),
          scan((acc, upd) => acc.map((item) => (item.id === upd.id ? upd : item)), initial),
        );

        return concat(of(initial), enrich$);
      }),
      catchError((err: HttpErrorResponse | Error) => {
        if (stale?.length) {
          return of(stale);
        }
        this.departamentosDetalleCache$ = null;
        const message =
          err instanceof Error ? err.message : mensajeApiError(err, 'No fue posible cargar los departamentos.');
        return throwError(() => new Error(message));
      }),
    );

    this.departamentosDetalleCache$ = (stale?.length ? concat(of(stale), network$) : network$).pipe(
      map((items) => items.filter((item) => !this.estaOculto(this.departamentosOcultosKey, item.id))),
      tap((items) => this.writeDepartamentosSession(items)),
      shareReplay(1),
    );

    return this.departamentosDetalleCache$;
  }

  responsablesDeDepartamento(departamentoId: number, force = false): Observable<ResponsableRef[]> {
    if (!force && this.responsablesCache.has(departamentoId)) {
      return of(this.responsablesCache.get(departamentoId)!);
    }
    return this.http.get<unknown>(apiUrl(`/departamentos/${departamentoId}/responsables`)).pipe(
      map((res) => this.asResponsableLista(res)),
      tap((items) => this.responsablesCache.set(departamentoId, items)),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No fue posible cargar los responsables.'))),
      ),
    );
  }

  contactosDeDepartamento(departamentoId: number): Observable<DepartamentoContacto[]> {
    return this.http.get<unknown>(apiUrl(`/departamentos/${departamentoId}/contactos`)).pipe(
      map((res) =>
        this.asArray(res)
          .map((item) => {
            const r = item as Record<string, unknown>;
            return {
              tipo: String(r['tipo'] ?? 'Contacto'),
              numero: String(r['valor'] ?? r['numero'] ?? ''),
              activo: r['activo'] == null ? true : Boolean(r['activo']),
            };
          })
          .filter((c) => c.numero),
      ),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No fue posible cargar los contactos.'))),
      ),
    );
  }

  private detalleDeDepartamento(d: CatalogoRef): Observable<DepartamentoItem> {
    return forkJoin({
      responsables: this.responsablesDeDepartamento(d.id).pipe(catchError(() => of([] as ResponsableRef[]))),
      contactos: this.contactosDeDepartamento(d.id).pipe(catchError(() => of([] as DepartamentoContacto[]))),
    }).pipe(
      map(({ responsables, contactos }) => ({
        id: d.id,
        nombre: d.nombre,
        activo: this.activoDepartamentoGuardado(d.id),
        responsables: responsables.map((r) => r.nombre).filter(Boolean),
        contactos,
      })),
    );
  }

  ocultarDepartamento(id: number): void {
    this.marcarOculto(this.departamentosOcultosKey, id);
    this.departamentosDetalleCache$ = null;
    const actual = this.readDepartamentosSession() ?? [];
    this.writeDepartamentosSession(actual.filter((item) => item.id !== id));
  }

  ocultarPrueba(id: number): void {
    this.marcarOculto(this.pruebasOcultosKey, id);
    this.pruebasCache$ = null;
    const actual = this.readPruebasSession() ?? [];
    this.writePruebasSession(actual.filter((item) => item.id !== id));
  }

  marcarDepartamentoActivo(id: number, activo: boolean): void {
    const actual = this.readDepartamentosSession() ?? [];
    const next = actual.map((item) => (item.id === id ? { ...item, activo } : item));
    this.writeDepartamentosSession(next);
    this.departamentosDetalleCache$ = null;
  }

  marcarPruebaActiva(id: number, activo: boolean): void {
    const mapa = this.leerPruebasActivo();
    mapa[id] = activo;
    try {
      sessionStorage.setItem(this.pruebasActivoKey, JSON.stringify(mapa));
    } catch {
      // ignore
    }
  }

  private estaOculto(key: string, id: number): boolean {
    return this.leerOcultos(key).includes(id);
  }

  private marcarOculto(key: string, id: number): void {
    const ids = this.leerOcultos(key);
    if (ids.includes(id)) {
      return;
    }
    try {
      sessionStorage.setItem(key, JSON.stringify([...ids, id]));
    } catch {
      // ignore
    }
  }

  private leerOcultos(key: string): number[] {
    try {
      const raw = sessionStorage.getItem(key);
      const parsed = raw ? (JSON.parse(raw) as number[]) : [];
      return Array.isArray(parsed) ? parsed.map(Number).filter((id) => id > 0) : [];
    } catch {
      return [];
    }
  }

  private activoPrueba(id: number): boolean {
    const valor = this.leerPruebasActivo()[id];
    return valor == null ? true : valor;
  }

  private activoDepartamentoGuardado(id: number): boolean {
    const prev = this.readDepartamentosSession()?.find((item) => item.id === id);
    return prev?.activo ?? true;
  }

  private leerPruebasActivo(): Record<number, boolean> {
    try {
      const raw = sessionStorage.getItem(this.pruebasActivoKey);
      return raw ? (JSON.parse(raw) as Record<number, boolean>) : {};
    } catch {
      return {};
    }
  }

  private asPruebaLista(res: unknown): PruebaCatalogo[] {
    return this.asArray(res)
      .map((item) => {
        const r = item as Record<string, unknown>;
        return {
          id: Number(r['id'] ?? 0),
          descripcion: String(r['descripcion'] ?? ''),
          resultadoEsperado: String(r['resultadoEsperado'] ?? ''),
        };
      })
      .filter((item) => item.id > 0);
  }

  private asRefLista(res: unknown): CatalogoRef[] {
    return this.asArray(res)
      .map((item) => {
        const r = item as Record<string, unknown>;
        return {
          id: Number(r['id'] ?? r['departamentoId'] ?? 0),
          nombre: String(r['nombre'] ?? r['descripcion'] ?? ''),
        };
      })
      .filter((item) => item.id > 0 && item.nombre);
  }

  private asResponsableLista(res: unknown): ResponsableRef[] {
    return this.asArray(res)
      .map((item) => {
        const r = item as Record<string, unknown>;
        return {
          id: Number(r['id'] ?? r['responsableId'] ?? 0),
          nombre: String(r['nombre'] ?? r['responsableNombre'] ?? ''),
        };
      })
      .filter((item) => item.id > 0);
  }

  private asArray(res: unknown): unknown[] {
    return Array.isArray(res) ? res : (res as { content?: unknown[] })?.content ?? [];
  }

  private readDepartamentosSession(): DepartamentoItem[] | null {
    try {
      const raw = sessionStorage.getItem(this.departamentosDetalleKey) ?? localStorage.getItem(this.departamentosDetalleKey);
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw) as DepartamentoItem[];
      return Array.isArray(parsed) ? parsed : null;
    } catch {
      return null;
    }
  }

  private writeDepartamentosSession(items: DepartamentoItem[]): void {
    try {
      const raw = JSON.stringify(items);
      sessionStorage.setItem(this.departamentosDetalleKey, raw);
      localStorage.setItem(this.departamentosDetalleKey, raw);
    } catch {
      // ignore
    }
  }

  private readPruebasSession(): PruebaCatalogo[] | null {
    try {
      const raw = sessionStorage.getItem(this.pruebasKey);
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw) as PruebaCatalogo[];
      return Array.isArray(parsed) ? parsed : null;
    } catch {
      return null;
    }
  }

  private writePruebasSession(items: PruebaCatalogo[]): void {
    try {
      sessionStorage.setItem(this.pruebasKey, JSON.stringify(items));
    } catch {
      // ignore
    }
  }
}
