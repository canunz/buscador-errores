import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import {
  Observable,
  catchError,
  forkJoin,
  map,
  of,
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
  private pruebasCache$: Observable<PruebaCatalogo[]> | null = null;
  private departamentosCache$: Observable<CatalogoRef[]> | null = null;
  private departamentosDetalleCache$: Observable<DepartamentoItem[]> | null = null;
  private responsablesCache = new Map<number, ResponsableRef[]>();

  snapshotPruebas(): PruebaCatalogo[] { return []; }
  invalidarPruebas(): void { this.pruebasCache$ = null; }
  listarPruebas(force = false): Observable<PruebaCatalogo[]> {
    if (!force && this.pruebasCache$) return this.pruebasCache$;
    this.pruebasCache$ = this.http.get<unknown>(apiUrl('/pruebas')).pipe(
      map(res => this.asPruebaLista(res)),
      catchError((err: HttpErrorResponse) => { this.pruebasCache$ = null; return throwError(() => new Error(mensajeApiError(err, 'No fue posible cargar el catálogo de pruebas.'))); }), shareReplay(1));
    return this.pruebasCache$;
  }
  listarPruebasDetalle(force = false): Observable<PruebaItem[]> {
    return this.listarPruebas(force).pipe(map(items => items.map(p => ({ ...p, activo: true }))));
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

  // Organización operacional: fuente HTTP, sin estado ni ocultación local.
  departamentosEnSesion(): DepartamentoItem[] { return []; }

  invalidarDepartamentos(): void {
    this.departamentosCache$ = null;
    this.departamentosDetalleCache$ = null;
    this.responsablesCache.clear();
  }

  listarDepartamentosDetalle(force = false): Observable<DepartamentoItem[]> {
    return this.listarDepartamentos(true).pipe(
      switchMap(deps => deps.length ? forkJoin(deps.map(d => this.detalleDeDepartamento(d))) : of([])),
    );
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
              id: Number(r['id'] ?? 0),
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
      responsables: this.responsablesDeDepartamento(d.id, true),
      contactos: this.contactosDeDepartamento(d.id),
    }).pipe(
      map(({ responsables, contactos }) => ({
        id: d.id,
        nombre: d.nombre,
        activo: true,
        responsables: responsables.map((r) => r.nombre).filter(Boolean),
        contactos,
      })),
    );
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

}
