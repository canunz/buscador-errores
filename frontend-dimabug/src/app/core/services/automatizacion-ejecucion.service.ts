import { plazoConsulta, MENSAJE_PLAZO_CONSULTA } from '../http/plazo-consulta';
import { TimeoutError } from 'rxjs';
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { apiUrl } from '../config/api';
import { mensajeApiError } from '../http/api-error';
import { ConfiguracionPaso, DestinatarioPaso } from '../models/configuracion-paso.model';
export interface SucursalEjecucion { id: number; codigo: string; nombre: string; responsableNombre: string; ciudad?: string | null; }
export interface DestinatarioSucursal { id: number; nombre: string; responsableNombre: string; responsableEmail: string; activa: boolean; ciudad?: string | null; responsableCargo?: string | null; destinatarios?: DestinatarioPaso[]; }
export interface SolicitudEvidencia {
  id: string; ejecucionId: number; ejecucionPasoId: number; ronda: number; version: number;
  contexto: { sucursalId: number | null; sucursalCodigo: string | null; sucursalNombre: string | null; responsableNombre: string | null; responsableEmail: string; destinatarios?: DestinatarioPaso[] };
  politica: { mensaje: string; primerRecordatorioHoras: number; segundoRecordatorioHoras: number; escalamientoHoras: number; configuracion?: ConfiguracionPaso };
  estado: string; fechaEnvio: string | null; mensajeEnviadoId: string | null; proximoSeguimiento: string | null; reglaAplicada: number;
}
export interface FotoEvidencia { id: number; categoria: 'MUEBLE' | 'RED' | 'ELECTRICIDAD' | 'FOTO_GENERAL'; sha256: string; }
export interface SolicitudConFotos { detalle: SolicitudEvidencia; fotos: FotoEvidencia[]; simulado: boolean; }
export interface ActividadEvidencia { id: number; solicitudId: string; tipo: string; detalle: string; fecha: string; }
export interface ComunicacionPaso { evento: ActividadEvidencia; pasoId: number; estado: string; responsable: string; destinatarios: DestinatarioPaso[]; evidencias: number[]; }
export interface EstadoAutomatizacion { comunicaciones?: ComunicacionPaso[]; solicitudes: SolicitudConFotos[]; actividad: ActividadEvidencia[]; disponible: boolean; simulado: boolean; }
export interface AvisoEvidencia { id: string; titulo: string; detalle: string; fecha: string | null; ruta: string; }
@Injectable({ providedIn: 'root' })
export class AutomatizacionEjecucionService {
  private readonly http = inject(HttpClient);
  sucursales() { return this.http.get<SucursalEjecucion[]>(apiUrl('/sucursales')).pipe(plazoConsulta(),this.errores()); }
  destinatario(id: number) { return this.http.get<DestinatarioSucursal>(apiUrl(`/sucursales/${id}/destinatario`)).pipe(plazoConsulta(),this.errores()); }
  estado(id: number) { return this.http.get<EstadoAutomatizacion>(apiUrl(`/ejecuciones/${id}/automatizacion`)).pipe(plazoConsulta(),this.errores()); }
  avisos() { return this.http.get<AvisoEvidencia[]>(apiUrl('/ejecuciones/avisos-evidencia')).pipe(plazoConsulta(),this.errores()); }
  enviar(id: number, paso: number, datos: { sucursalId?: number; destinatarios: DestinatarioPaso[]; asunto: string; mensaje: string }) {
    return this.http.post<SolicitudEvidencia>(apiUrl(`/ejecuciones/${id}/pasos/${paso}/correo`), datos).pipe(this.errores());
  }
  simular(id: number, solicitud: string, remitente: string, mensajeId: string) {
    return this.http.post<SolicitudEvidencia>(this.base(id, solicitud) + '/simular-respuesta', { remitente, mensajeId }).pipe(this.errores());
  }
  subir(id: number, solicitud: string, remitente: string, mensajeId: string, fotos: Record<string, File>) {
    const body = new FormData(); body.append('remitente', remitente); body.append('mensajeId', mensajeId);
    for (const nombre of Object.keys(fotos)) body.append(nombre, fotos[nombre]);
    return this.http.post<SolicitudEvidencia>(this.base(id, solicitud) + '/evidencias', body).pipe(this.errores());
  }
  decidir(id: number, s: SolicitudEvidencia, aprobar: boolean, motivo: string, definitiva = false, separarSolicitud = false) {
    return this.http.post<SolicitudEvidencia>(this.base(id, s.id) + '/decision', { version: s.version, aprobar, motivo, definitiva, solicitarNuevamente:!separarSolicitud }).pipe(this.errores());
  }
  solicitarNuevamente(id:number,s:SolicitudEvidencia){return this.http.post<SolicitudEvidencia>(this.base(id,s.id)+'/nueva-solicitud',{version:s.version}).pipe(plazoConsulta(),this.errores());}
  tiempo(id: number, solicitud: string, horas: number) { return this.http.post<void>(this.base(id, solicitud) + '/simular-tiempo', { horas }).pipe(this.errores()); }
  confirmar(id:number,solicitud:string,remitente:string,mensajeId:string,texto:string){return this.http.post<SolicitudEvidencia>(this.base(id,solicitud)+'/simular-confirmacion',{remitente,mensajeId,texto}).pipe(this.errores());}
  foto(id: number, solicitud: string, foto: number) { return this.http.get(this.base(id, solicitud) + `/fotos/${foto}`, { responseType: 'blob' }).pipe(plazoConsulta(),this.errores()); }
  private base(id: number, solicitud: string) { return apiUrl(`/ejecuciones/${id}/solicitudes-evidencia/${encodeURIComponent(solicitud)}`); }
  private errores<T>() { return catchError<T, Observable<never>>((err: HttpErrorResponse) => throwError(() => new Error(err instanceof TimeoutError?MENSAJE_PLAZO_CONSULTA:mensajeApiError(err, 'No fue posible confirmar la automatización. Actualiza antes de reintentar.')))); }
}
