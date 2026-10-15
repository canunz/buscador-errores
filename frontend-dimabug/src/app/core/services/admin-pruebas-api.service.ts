import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, map, tap, catchError, throwError } from 'rxjs';
import { apiUrl } from '../config/api';
import { mensajeApiError } from '../http/api-error';
import { OrganizacionService } from './organizacion.service';
import { PruebaItem } from '../models/catalogo.model';
interface PruebaRespuesta { id: number; descripcion: string; resultadoEsperado: string | null; activa: boolean; }
@Injectable({ providedIn: 'root' })
export class AdminPruebasApiService {
  private readonly http = inject(HttpClient);
  private readonly organizacion = inject(OrganizacionService);
  private readonly base = apiUrl('/admin/pruebas');
  listar(): Observable<PruebaItem[]> { return this.http.get<PruebaRespuesta[]>(this.base).pipe(map(items => items.map(p => this.normalizar(p))), this.errores()); }
  guardar(id: number | null, descripcion: string, resultadoEsperado: string): Observable<PruebaItem> {
    const datos = { descripcion: descripcion.trim(), resultadoEsperado: resultadoEsperado.trim() || null };
    const req = id == null ? this.http.post<PruebaRespuesta>(this.base, datos) : this.http.put<PruebaRespuesta>(`${this.base}/${id}`, datos);
    return req.pipe(map(p => this.normalizar(p)), tap(() => this.organizacion.invalidarPruebas()), this.errores());
  }
  estado(id: number, activa: boolean): Observable<PruebaItem> {
    return this.http.patch<PruebaRespuesta>(`${this.base}/${id}/estado`, { activa }).pipe(map(p => this.normalizar(p)), tap(() => this.organizacion.invalidarPruebas()), this.errores());
  }
  private normalizar(p: PruebaRespuesta): PruebaItem { return { id: p.id, descripcion: p.descripcion, resultadoEsperado: p.resultadoEsperado ?? '', activo: p.activa }; }
  private errores<T>() { return catchError<T, Observable<never>>((e: HttpErrorResponse) => throwError(() => new Error(mensajeApiError(e, 'No fue posible consultar o guardar la prueba.')))); }
}
