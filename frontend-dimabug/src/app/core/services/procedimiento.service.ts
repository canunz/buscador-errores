import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, map, tap, throwError } from 'rxjs';
import { apiUrl } from '../config/api';
import { mensajeApiError } from '../http/api-error';
import { crearFormDataMaterial, descargarMaterialAutenticado } from '../material/material-archivo';
import {
  EstadoProcedimiento,
  GuardarMaterialPasoRequest,
  GuardarPasoRequest,
  GuardarProcedimientoRequest,
  MaterialPaso,
  PasoProcedimiento,
  Procedimiento,
  TipoMaterialProcedimiento,
  UsuarioProcedimiento,
} from '../models/procedimiento.model';

@Injectable({ providedIn: 'root' })
export class ProcedimientoService {
  private readonly http = inject(HttpClient);
  private readonly base = apiUrl('/procedimientos');
  private readonly cacheProcedimientos = new Map<number, Procedimiento>();
  private readonly cachePasos = new Map<number, PasoProcedimiento[]>();
  private readonly cacheMateriales = new Map<number, MaterialPaso[]>();

  procedimientoEnCache(id: number): Procedimiento | null {
    return this.cacheProcedimientos.get(id) ?? null;
  }

  pasosEnCache(procedimientoId: number): PasoProcedimiento[] | null {
    return this.cachePasos.get(procedimientoId) ?? null;
  }

  materialesEnCache(pasoId: number): MaterialPaso[] | null {
    return this.cacheMateriales.get(pasoId) ?? null;
  }

  listar(): Observable<Procedimiento[]> {
    return this.http.get<unknown>(this.base).pipe(
      map((res) => this.asArray(res).map((item) => this.normalizeProcedimiento(item))),
      tap((items) => items.forEach((item) => this.cacheProcedimientos.set(item.id, item))),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
    );
  }

  obtenerPorId(id: number): Observable<Procedimiento> {
    return this.http.get<unknown>(`${this.base}/${id}`).pipe(
      map((res) => this.normalizeProcedimiento(res)),
      tap((item) => this.cacheProcedimientos.set(item.id, item)),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
    );
  }

  crear(request: GuardarProcedimientoRequest): Observable<Procedimiento> {
    return this.http.post<unknown>(this.base, this.cuerpoProcedimiento(request)).pipe(
      map((res) => this.normalizeProcedimiento(res)),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
    );
  }

  actualizar(id: number, request: GuardarProcedimientoRequest): Observable<Procedimiento> {
    return this.http.put<unknown>(`${this.base}/${id}`, this.cuerpoProcedimiento(request)).pipe(
      map((res) => this.normalizeProcedimiento(res)),
      tap((item) => this.cacheProcedimientos.set(item.id, item)),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
    );
  }

  cambiarEstado(id: number, estado: EstadoProcedimiento): Observable<Procedimiento> {
    return this.http.patch<unknown>(`${this.base}/${id}/estado`, { estado }).pipe(
      map((res) => this.normalizeProcedimiento(res)),
      tap((item) => this.cacheProcedimientos.set(item.id, item)),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
    );
  }

  listarPasos(procedimientoId: number): Observable<PasoProcedimiento[]> {
    return this.http.get<unknown>(`${this.base}/${procedimientoId}/pasos`).pipe(
      map((res) => this.asArray(res).map((item) => this.normalizePaso(item))),
      tap((pasos) => this.cachePasos.set(procedimientoId, pasos)),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
    );
  }

  crearPaso(procedimientoId: number, request: GuardarPasoRequest): Observable<PasoProcedimiento> {
    return this.http.post<unknown>(`${this.base}/${procedimientoId}/pasos`, request).pipe(
      map((res) => this.normalizePaso(res)),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
    );
  }

  actualizarPaso(
    procedimientoId: number,
    pasoId: number,
    request: GuardarPasoRequest,
  ): Observable<PasoProcedimiento> {
    return this.http.put<unknown>(`${this.base}/${procedimientoId}/pasos/${pasoId}`, request).pipe(
      map((res) => this.normalizePaso(res)),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
    );
  }

  listarMateriales(procedimientoId: number, pasoId: number): Observable<MaterialPaso[]> {
    return this.http.get<unknown>(`${this.base}/${procedimientoId}/pasos/${pasoId}/materiales`).pipe(
      map((res) => this.asArray(res).map((item) => this.normalizeMaterial(item))),
      tap((items) => this.cacheMateriales.set(pasoId, items)),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
    );
  }

  crearMaterial(
    procedimientoId: number,
    pasoId: number,
    request: GuardarMaterialPasoRequest,
  ): Observable<MaterialPaso> {
    return this.http
      .post<unknown>(`${this.base}/${procedimientoId}/pasos/${pasoId}/materiales`, request)
      .pipe(
        map((res) => this.normalizeMaterial(res)),
        catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
      );
  }

  crearMaterialArchivo(
    procedimientoId: number,
    pasoId: number,
    nombre: string,
    tipo: TipoMaterialProcedimiento,
    archivo: File,
  ): Observable<MaterialPaso> {
    return this.http
      .post<unknown>(
        `${this.base}/${procedimientoId}/pasos/${pasoId}/materiales/archivo`,
        crearFormDataMaterial(nombre, tipo, archivo),
      )
      .pipe(
        map((res) => this.normalizeMaterial(res)),
        catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
      );
  }

  descargarMaterial(material: MaterialPaso): Observable<void> {
    return descargarMaterialAutenticado(this.http, material.url, material.nombre, material.tipo).pipe(
      catchError((err: HttpErrorResponse | Error) =>
        throwError(() => (err instanceof HttpErrorResponse ? new Error(this.mensaje(err)) : err)),
      ),
    );
  }

  eliminarMaterial(procedimientoId: number, pasoId: number, materialId: number): Observable<void> {
    return this.http
      .delete(`${this.base}/${procedimientoId}/pasos/${pasoId}/materiales/${materialId}`, { responseType: 'text' })
      .pipe(
        map(() => undefined),
        tap(() => {
          const actuales = this.cacheMateriales.get(pasoId);
          if (actuales) this.cacheMateriales.set(pasoId, actuales.filter((m) => m.id !== materialId));
        }),
        catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
      );
  }

  actualizarMaterial(
    procedimientoId: number,
    pasoId: number,
    materialId: number,
    request: GuardarMaterialPasoRequest,
  ): Observable<MaterialPaso> {
    return this.http
      .put<unknown>(`${this.base}/${procedimientoId}/pasos/${pasoId}/materiales/${materialId}`, request)
      .pipe(
        map((res) => this.normalizeMaterial(res)),
        catchError((err: HttpErrorResponse) => throwError(() => new Error(this.mensaje(err)))),
      );
  }

  private cuerpoProcedimiento(request: GuardarProcedimientoRequest): GuardarProcedimientoRequest {
    return {
      nombre: request.nombre.trim(),
      descripcion: request.descripcion?.trim() ? request.descripcion.trim() : null,
    };
  }

  private mensaje(err: HttpErrorResponse): string {
    if (err.status === 403) {
      return 'No tienes permisos para realizar esta acción.';
    }
    if (err.status === 404) {
      return 'El procedimiento o recurso solicitado no está disponible.';
    }
    if (err.status === 409) {
      return 'Ya existe un paso con ese número en este procedimiento.';
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

  private normalizeProcedimiento(raw: unknown): Procedimiento {
    const r = (raw ?? {}) as Record<string, unknown>;
    const estado = String(r['estado'] ?? 'BORRADOR').toUpperCase();
    return {
      id: Number(r['id'] ?? 0),
      nombre: String(r['nombre'] ?? ''),
      descripcion: r['descripcion'] != null ? String(r['descripcion']) : null,
      estado: estado === 'PUBLICADO' ? 'PUBLICADO' : 'BORRADOR',
      creadoPor: this.usuario(r['creadoPor']),
      fechaCreacion: r['fechaCreacion'] != null ? String(r['fechaCreacion']) : null,
      modificadoPor: this.usuario(r['modificadoPor']),
      fechaModificacion: r['fechaModificacion'] != null ? String(r['fechaModificacion']) : null,
    };
  }

  private usuario(raw: unknown): UsuarioProcedimiento | null {
    if (!raw || typeof raw !== 'object') {
      return null;
    }
    const r = raw as Record<string, unknown>;
    return {
      id: Number(r['id'] ?? 0),
      nombre: String(r['nombre'] ?? ''),
    };
  }

  private normalizePaso(raw: unknown): PasoProcedimiento {
    const r = (raw ?? {}) as Record<string, unknown>;
    return {
      id: Number(r['id'] ?? 0),
      procedimientoId: Number(r['procedimientoId'] ?? 0),
      orden: Number(r['orden'] ?? 0),
      instruccion: String(r['instruccion'] ?? ''),
      esCritico: r['esCritico'] === true,
    };
  }

  private normalizeMaterial(raw: unknown): MaterialPaso {
    const r = (raw ?? {}) as Record<string, unknown>;
    const tipo = String(r['tipo'] ?? 'ENLACE').toUpperCase();
    const permitido: TipoMaterialProcedimiento[] = ['IMAGEN', 'PDF', 'VIDEO', 'ENLACE'];
    return {
      id: Number(r['id'] ?? 0),
      nombre: String(r['nombre'] ?? ''),
      tipo: (permitido.includes(tipo as TipoMaterialProcedimiento) ? tipo : 'ENLACE') as TipoMaterialProcedimiento,
      url: String(r['url'] ?? ''),
    };
  }
}
