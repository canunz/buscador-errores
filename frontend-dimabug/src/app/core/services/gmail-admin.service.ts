import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError, Observable, timeout } from 'rxjs';
import { apiUrl } from '../config/api';
import { mensajeApiError } from '../http/api-error';
import { plazoConsulta } from '../http/plazo-consulta';
export interface EstadoGmail {configurado:boolean;conectado:boolean;envioRealHabilitado:boolean;estado:string;expira:string|null;remitente:string;revocacionRemotaPendiente:boolean;}
export interface ResultadoPruebaGmail {id:string;actor:number;remitente:string;destinatario:string;estado:'PENDIENTE'|'ENVIANDO'|'ENVIADO'|'FALLIDO'|'INCIERTO';creada:string;intento:string|null;finalizada:string|null;mensajeId:string|null;resultado:string|null;}
export interface EstadoPruebaGmail {remitente:string;destinatario:string;conectado:boolean;transporte:string;habilitado:boolean;ultima:ResultadoPruebaGmail|null;}
@Injectable({providedIn:'root'})
export class GmailAdminService {
  private readonly http=inject(HttpClient);
  estado(){return this.http.get<EstadoGmail>(apiUrl('/admin/gmail')).pipe(plazoConsulta(),this.errores());}
  autorizar(){return this.http.post<{authorizationUrl:string}>(apiUrl('/admin/gmail/autorizacion'),{}).pipe(plazoConsulta(),this.errores());}
  revocar(){return this.http.post<EstadoGmail>(apiUrl('/admin/gmail/revocar'),{}).pipe(plazoConsulta(),this.errores());}
  pruebaEstado(){return this.http.get<EstadoPruebaGmail>(apiUrl('/admin/gmail/prueba')).pipe(plazoConsulta(),this.errores());}
  enviarPrueba(datos:{clave:string;anterior:string|null;confirmado:true;destinatario:string}){return this.http.post<ResultadoPruebaGmail>(apiUrl('/admin/gmail/prueba'),datos).pipe(timeout(45000),this.errores());}
  private errores<T>(){return catchError<T,Observable<never>>((e:HttpErrorResponse)=>throwError(()=>new Error(mensajeApiError(e,'No fue posible confirmar la operación con Gmail. Actualiza su estado antes de reintentar.'))));}
}
