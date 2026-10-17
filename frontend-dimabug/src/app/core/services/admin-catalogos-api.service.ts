import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, tap, throwError } from 'rxjs';
import { apiUrl } from '../config/api';
import { mensajeApiError } from '../http/api-error';
import { ClasificacionService } from './clasificacion.service';
export type TipoCatalogoAdmin = 'hardware' | 'sistemas' | 'modulos' | 'frecuencias';
export const TIPOS_CATALOGO_ADMIN: TipoCatalogoAdmin[] = ['hardware', 'sistemas', 'modulos', 'frecuencias'];
export interface CatalogoAdmin {
  id: number; nombre: string; activo: boolean; sistemaOperativo: string | null;
  descripcion: string | null; sistemaId: number | null; orden: number | null; sistemaIds: number[];
}
export type CatalogoAdminRequest = Omit<CatalogoAdmin, 'id'>;
@Injectable({ providedIn: 'root' })
export class AdminCatalogosApiService {
  private readonly http = inject(HttpClient);
  private readonly clasificacion = inject(ClasificacionService);
  listar(tipo: TipoCatalogoAdmin): Observable<CatalogoAdmin[]> {
    return this.http.get<CatalogoAdmin[]>(this.url(tipo)).pipe(this.errores());
  }
  guardar(tipo: TipoCatalogoAdmin, id: number | null, datos: CatalogoAdminRequest): Observable<CatalogoAdmin> {
    const request = id === null ? this.http.post<CatalogoAdmin>(this.url(tipo), datos)
      : this.http.put<CatalogoAdmin>(`${this.url(tipo)}/${id}`, datos);
    return request.pipe(tap(() => this.clasificacion.invalidarCatalogos()), this.errores());
  }
  cambiarEstado(tipo: TipoCatalogoAdmin, id: number, activo: boolean): Observable<CatalogoAdmin> {
    return this.http.patch<CatalogoAdmin>(`${this.url(tipo)}/${id}/estado`, { activo }).pipe(
      tap(() => this.clasificacion.invalidarCatalogos()), this.errores());
  }
  private url(tipo: TipoCatalogoAdmin): string { return apiUrl(`/admin/catalogos/${tipo}`); }
  private errores<T>() {
    return catchError<T, Observable<never>>((err: HttpErrorResponse) =>
      throwError(() => new Error(mensajeApiError(err, 'No fue posible completar la operación del catálogo.'))));
  }
}
