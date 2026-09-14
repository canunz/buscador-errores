import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, map, of, throwError } from 'rxjs';
import { apiUrl } from '../config/api';
import { mensajeApiError } from '../http/api-error';
import { CatalogoRef, FrecuenciaCatalogo } from '../models/conocimiento.model';

@Injectable({ providedIn: 'root' })
export class ClasificacionService {
  private readonly http = inject(HttpClient);

  listarHardware(): Observable<CatalogoRef[]> {
    return this.getLista('/hardware', 'No fue posible cargar el hardware.');
  }

  sistemasDeHardware(hardwareId: number): Observable<CatalogoRef[]> {
    return this.getLista(`/hardware/${hardwareId}/sistemas`, 'No fue posible cargar los sistemas.');
  }

  listarSistemas(): Observable<CatalogoRef[]> {
    return this.getLista('/sistemas', 'No fue posible cargar los sistemas.');
  }

  obtenerSistema(id: number): Observable<CatalogoRef> {
    return this.http.get<unknown>(apiUrl(`/sistemas/${id}`)).pipe(
      map((res) => this.asItem(res)),
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(mensajeApiError(err, 'No se encontró el sistema.'))),
      ),
    );
  }

  hardwareDeSistema(sistemaId: number): Observable<CatalogoRef[]> {
    return this.getLista(`/sistemas/${sistemaId}/hardware`, 'No fue posible cargar el hardware del sistema.');
  }

  modulosDeSistema(sistemaId: number): Observable<CatalogoRef[]> {
    return this.getLista(`/sistemas/${sistemaId}/modulos`, 'No fue posible cargar los módulos.');
  }

  listarFrecuencias(): Observable<FrecuenciaCatalogo> {
    return this.http.get<unknown>(apiUrl('/frecuencias')).pipe(
      map((res) => ({ items: this.asLista(res), disponible: true })),
      catchError(() => of({ items: [], disponible: false })),
    );
  }

  private getLista(path: string, fallback: string): Observable<CatalogoRef[]> {
    return this.http.get<unknown>(apiUrl(path)).pipe(
      map((res) => this.asLista(res)),
      catchError((err: HttpErrorResponse) => throwError(() => new Error(mensajeApiError(err, fallback)))),
    );
  }

  private asLista(res: unknown): CatalogoRef[] {
    const list = Array.isArray(res) ? res : (res as { content?: unknown[] })?.content ?? [];
    return list.map((item) => this.asItem(item)).filter((item) => item.id > 0 && item.nombre);
  }

  private asItem(raw: unknown): CatalogoRef {
    const r = (raw ?? {}) as Record<string, unknown>;
    return {
      id: Number(r['id'] ?? r['hardwareId'] ?? r['sistemaId'] ?? r['moduloId'] ?? r['frecuenciaId'] ?? 0),
      nombre: String(r['nombre'] ?? r['descripcion'] ?? r['titulo'] ?? ''),
    };
  }
}
