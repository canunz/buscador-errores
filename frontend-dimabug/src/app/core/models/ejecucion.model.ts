import { ModalidadPaso, ConfiguracionPaso } from './configuracion-paso.model';
export type EstadoEjecucion = 'EN_CURSO' | 'COMPLETADA' | 'CANCELADA';
export type ModoEjecucionPaso = ModalidadPaso;
export type EstadoEjecucionPaso = 'PENDIENTE' | 'EN_PROCESO' | 'ESPERANDO_RESPUESTA' |
  'ESPERANDO_CONFIRMACION' | 'ESPERANDO_EVIDENCIA' | 'EVIDENCIA_RECIBIDA' | 'EN_VALIDACION' | 'RECHAZADO' | 'COMPLETADO' | 'ERROR';

export interface EjecucionUsuario {
  id: number;
  nombre: string;
  email: string;
}

export interface Ejecucion {
  id: number;
  procedimientoId: number;
  estado: EstadoEjecucion;
  fechaInicio: string | null;
  fechaFin: string | null;
  observaciones: string | null;
  usuario: EjecucionUsuario;
}

export interface EjecucionPaso {
  ejecucionPasoId: number;
  pasoId: number;
  orden: number;
  instruccion: string;
  esCritico: boolean;
  cumplido: boolean;
  observacion: string | null;
  fecha: string | null;
  estado?: EstadoEjecucionPaso;
  version?: number;
  contratoVersion?: number | null;
  modoEjecucion?: ModoEjecucionPaso;
  configuracion?: ConfiguracionPaso | null;
}

export interface ActualizarEjecucionPasoRequest {
  cumplido?: boolean;
  observacion?: string | null;
  versionEsperada?: number;
}

export interface CancelarEjecucionRequest {
  observaciones?: string | null;
}

export type EstadoProcedimiento = 'BORRADOR' | 'PUBLICADO';

export interface ProcedimientoResumen {
  id: number;
  nombre: string;
  descripcion: string | null;
  estado: EstadoProcedimiento;
}
