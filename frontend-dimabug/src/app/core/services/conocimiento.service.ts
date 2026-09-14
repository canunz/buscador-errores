import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, map, throwError } from 'rxjs';
import { apiUrl } from '../config/api';
import { mensajeApiError } from '../http/api-error';
import {
  Conocimiento,
  ConocimientoEstado,
  ConocimientoRequest,
} from '../models/conocimiento.model';

@Injectable({ providedIn: 'root' })
export class ConocimientoService {
  private readonly http = inject(HttpClient);
  private readonly base = apiUrl('/conocimientos');

  listar(): Observable<Conocimiento[]> {
    return this.http.get<unknown>(this.base).pipe(
      map((res) => this.asLista(res)),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No fue posible cargar los conocimientos.'))),
      ),
    );
  }

  obtenerPorId(id: number): Observable<Conocimiento> {
    return this.http.get<unknown>(`${this.base}/${id}`).pipe(
      map((res) => this.normalize(res)),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No se encontró el conocimiento.'))),
      ),
    );
  }

  crear(request: ConocimientoRequest): Observable<Conocimiento> {
    return this.http.post<unknown>(this.base, request).pipe(
      map((res) => this.normalize(res)),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No fue posible guardar el conocimiento.'))),
      ),
    );
  }

  modificar(id: number, request: ConocimientoRequest): Observable<Conocimiento> {
    return this.http.put<unknown>(`${this.base}/${id}`, request).pipe(
      map((res) => this.normalize(res)),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No fue posible actualizar el conocimiento.'))),
      ),
    );
  }

  cambiarEstado(id: number, estado: ConocimientoEstado): Observable<Conocimiento> {
    return this.http.patch<unknown>(`${this.base}/${id}/estado`, { estado }).pipe(
      map((res) => this.normalize(res)),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No fue posible cambiar el estado.'))),
      ),
    );
  }

  private asLista(res: unknown): Conocimiento[] {
    const list = Array.isArray(res) ? res : (res as { content?: unknown[] })?.content ?? [];
    return list.map((item) => this.normalize(item));
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
      estado: estadoRaw === 'PUBLICADO' ? 'PUBLICADO' : 'BORRADOR',
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
