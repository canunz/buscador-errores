import { ModalidadPaso, ConfiguracionPaso } from './configuracion-paso.model';
export type EstadoProcedimiento = 'BORRADOR' | 'PUBLICADO';

export interface UsuarioProcedimiento {
  id: number;
  nombre: string;
}

export interface Procedimiento {
  hardwareId?:number|null;
  sistemaId?:number|null;moduloId?:number|null;informacionComplementaria?:string|null;origenId?:number|null;revision?:number;vigente?:boolean;
  id: number;
  nombre: string;
  descripcion: string | null;
  estado: EstadoProcedimiento;
  creadoPor: UsuarioProcedimiento | null;
  fechaCreacion: string | null;
  modificadoPor: UsuarioProcedimiento | null;
  fechaModificacion: string | null;
}

export interface GuardarProcedimientoRequest {
  hardwareId?:number|null;
  sistemaId?:number|null;moduloId?:number|null;informacionComplementaria?:string|null;estado?:EstadoProcedimiento;
  nombre: string;
  descripcion: string | null;
}

export interface PasoProcedimiento {
  id: number;
  procedimientoId: number;
  orden: number;
  instruccion: string;
  esCritico: boolean;
  modoEjecucion?: ModalidadPaso;
  configuracion?: ConfiguracionPaso | null;
  revisionConfiguracion?: string;
  mensajeEvidencia?: string | null;
}

export interface GuardarPasoRequest {
  orden: number;
  instruccion: string;
  esCritico: boolean;
  modoEjecucion?: ModalidadPaso;
  configuracion?: ConfiguracionPaso | null;
  revisionConfiguracion?: string;
  mensajeEvidencia?: string | null;
}

export type TipoMaterialProcedimiento = 'IMAGEN' | 'PDF' | 'VIDEO' | 'ENLACE';

export interface MaterialPaso {
  id: number;
  nombre: string;
  tipo: TipoMaterialProcedimiento;
  url: string;
}

export interface GuardarMaterialPasoRequest {
  nombre: string;
  tipo: TipoMaterialProcedimiento;
  url: string;
}
