import { DestinatarioPaso } from '../models/configuracion-paso.model';
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { apiUrl } from '../config/api';
import { mensajeApiError } from '../http/api-error';

export interface SucursalAdmin {
  id: number; codigo: string; nombre: string; responsableNombre: string; responsableEmail: string;
  ciudad?: string | null; responsableCargo?: string | null; activa: boolean; version: number; destinatarios?: DestinatarioPaso[];
}
export type DatosSucursal = Pick<SucursalAdmin, 'codigo' | 'nombre' | 'responsableNombre' | 'responsableEmail'> & { destinatarios?: DestinatarioPaso[]; ciudad?: string | null; responsableCargo?: string | null };

@Injectable({ providedIn: 'root' })
export class AdminSucursalesApiService {
  private readonly http = inject(HttpClient);
  listar(): Observable<SucursalAdmin[]> { return this.http.get<SucursalAdmin[]>(apiUrl('/admin/sucursales')).pipe(this.errores()); }
  crear(datos: DatosSucursal): Observable<SucursalAdmin> { return this.http.post<SucursalAdmin>(apiUrl('/admin/sucursales'), datos).pipe(this.errores()); }
  editar(id: number, datos: DatosSucursal, version: number): Observable<SucursalAdmin> {
    return this.http.put<SucursalAdmin>(apiUrl(`/admin/sucursales/${id}`), { ...datos, version }).pipe(this.errores());
  }
  estado(sucursal: SucursalAdmin, activa: boolean): Observable<SucursalAdmin> {
    return this.http.patch<SucursalAdmin>(apiUrl(`/admin/sucursales/${sucursal.id}/estado`), { activa, version: sucursal.version }).pipe(this.errores());
  }
  private errores<T>() {
    return catchError<T, Observable<never>>((err: HttpErrorResponse) =>
      throwError(() => new Error(mensajeApiError(err, 'No fue posible actualizar el catálogo de sucursales.'))));
  }
}
