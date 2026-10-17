import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, tap, throwError } from 'rxjs';
import { apiUrl } from '../config/api';
import { mensajeApiError } from '../http/api-error';
import { OrganizacionService } from './organizacion.service';
import { DepartamentoAdmin, ContactoDepartamentoAdmin } from '../models/organizacion-admin.model';
@Injectable({ providedIn: 'root' })
export class AdminOrganizacionApiService {
  private readonly http = inject(HttpClient);
  private readonly organizacion = inject(OrganizacionService);
  private readonly base = apiUrl('/admin/departamentos');
  listar(): Observable<DepartamentoAdmin[]> { return this.http.get<DepartamentoAdmin[]>(this.base).pipe(this.errores()); }
  guardar(id: number | null, nombre: string, version?: number): Observable<DepartamentoAdmin> {
    const req = id == null ? this.http.post<DepartamentoAdmin>(this.base, { nombre }) : this.http.put<DepartamentoAdmin>(`${this.base}/${id}`, { nombre, version });
    return req.pipe(tap(() => this.organizacion.invalidarDepartamentos()), this.errores());
  }
  estado(id: number, activo: boolean, version: number): Observable<DepartamentoAdmin> {
    return this.http.patch<DepartamentoAdmin>(`${this.base}/${id}/estado`, { activo, version }).pipe(tap(() => this.organizacion.invalidarDepartamentos()), this.errores());
  }
  contactos(id: number): Observable<ContactoDepartamentoAdmin[]> {
    return this.http.get<ContactoDepartamentoAdmin[]>(`${this.base}/${id}/contactos`).pipe(this.errores());
  }
  guardarContacto(padre: number, id: number | null, datos: { tipo: string; valor: string; activo?: boolean; version?: number }): Observable<ContactoDepartamentoAdmin> {
    const url = `${this.base}/${padre}/contactos`;
    const req = id == null ? this.http.post<ContactoDepartamentoAdmin>(url, { tipo: datos.tipo, valor: datos.valor, activo: datos.activo })
      : this.http.put<ContactoDepartamentoAdmin>(`${url}/${id}`, { tipo: datos.tipo, valor: datos.valor, version: datos.version });
    return req.pipe(tap(() => this.organizacion.invalidarDepartamentos()), this.errores());
  }
  estadoContacto(padre: number, id: number, activo: boolean, version: number): Observable<ContactoDepartamentoAdmin> {
    return this.http.patch<ContactoDepartamentoAdmin>(`${this.base}/${padre}/contactos/${id}/estado`, { activo, version }).pipe(tap(() => this.organizacion.invalidarDepartamentos()), this.errores());
  }
  private errores<T>() { return catchError<T, Observable<never>>((err: HttpErrorResponse) => throwError(() => new Error(mensajeApiError(err, 'No fue posible guardar o consultar la organización.')))); }
}
