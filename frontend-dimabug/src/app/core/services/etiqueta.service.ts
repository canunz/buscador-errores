import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, catchError, map, throwError } from 'rxjs';
import { apiUrl } from '../config/api';
import { Etiqueta, EtiquetaRequest } from '../models/ticket.model';
import { mensajeTicketError } from './ticket.service';

@Injectable({ providedIn: 'root' })
export class EtiquetaService {
  private readonly http = inject(HttpClient);
  private readonly base = apiUrl('/etiquetas');

  /** Activas. El administrador puede pedir también las inactivas. */
  listar(incluirInactivas = false): Observable<Etiqueta[]> {
    const params = new HttpParams().set('incluirInactivas', String(incluirInactivas));
    return this.http.get<Etiqueta[]>(this.base, { params }).pipe(
      map((lista) => (lista ?? []).map((e) => ({ ...e, nivel: e.nivel ?? 'NORMAL' }))),
      this.error('No fue posible cargar las etiquetas.'),
    );
  }

  crear(request: EtiquetaRequest): Observable<Etiqueta> {
    return this.http.post<Etiqueta>(this.base, request).pipe(this.error('No fue posible crear la etiqueta.'));
  }

  editar(id: number, request: EtiquetaRequest): Observable<Etiqueta> {
    return this.http.put<Etiqueta>(`${this.base}/${id}`, request).pipe(this.error('No fue posible guardar la etiqueta.'));
  }

  cambiarEstado(id: number, activa: boolean): Observable<Etiqueta> {
    return this.http
      .patch<Etiqueta>(`${this.base}/${id}/estado`, { activa })
      .pipe(this.error('No fue posible cambiar el estado de la etiqueta.'));
  }

  private error<T>(fallback: string) {
    return catchError<T, Observable<T>>((err: HttpErrorResponse) =>
      throwError(() => new Error(mensajeTicketError(err, fallback))),
    );
  }
}
