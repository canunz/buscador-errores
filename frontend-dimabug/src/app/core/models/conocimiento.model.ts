export type ConocimientoEstado = 'BORRADOR' | 'PUBLICADO';

export interface CatalogoRef {
  id: number;
  nombre: string;
}

export interface Conocimiento {
  id: number;
  titulo: string;
  descripcion: string;
  comentario: string | null;
  estado: ConocimientoEstado;
  hardwareId: number | null;
  sistemaId: number | null;
  moduloId: number | null;
  frecuenciaId: number | null;
  hardwareNombre: string;
  sistemaNombre: string;
  moduloNombre: string;
  frecuenciaNombre: string;
  creadoPor: string;
  fechaCreacion: string | null;
  fechaModificacion: string | null;
}

export interface ConocimientoRequest {
  titulo: string;
  descripcion: string;
  hardwareId: number | null;
  sistemaId: number | null;
  moduloId: number | null;
  frecuenciaId: number | null;
  comentario: string | null;
}

export interface FrecuenciaCatalogo {
  items: CatalogoRef[];
  disponible: boolean;
}
