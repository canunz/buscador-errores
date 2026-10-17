export type EstadoTicket = 'ABIERTO' | 'EN_PROGRESO' | 'EN_ESPERA' | 'RESUELTO' | 'CERRADO' | 'CANCELADO';
export type PrioridadTicket = 'BAJA' | 'MEDIA' | 'ALTA' | 'CRITICA';

export interface Referencia {
  id: number;
  nombre: string | null;
}

export type NivelEtiqueta = 'NORMAL' | 'IMPORTANTE' | 'CRITICA';

export interface Etiqueta {
  id: number;
  nombre: string;
  color: string;
  activa: boolean;
  nivel: NivelEtiqueta;
}

export interface TicketResumen {
  id: number;
  codigo: string;
  titulo: string;
  estado: EstadoTicket;
  prioridad: PrioridadTicket;
  solicitante: Referencia | null;
  asignado: Referencia | null;
  etiquetas: Etiqueta[];
  fechaCreacion: string;
  fechaActualizacion: string;
}

export interface ComentarioTicket {
  id: number;
  autor: Referencia | null;
  texto: string;
  interno: boolean;
  fecha: string;
}

export interface HistorialEstado {
  id: number;
  estadoAnterior: EstadoTicket | null;
  estadoNuevo: EstadoTicket;
  usuario: Referencia | null;
  motivo: string | null;
  fecha: string;
}

export interface ConocimientoVinculado {
  id: number;
  titulo: string | null;
  estado: string | null;
}

export interface TicketDetalle extends TicketResumen {
  descripcion: string;
  hardware: Referencia | null;
  sistema: Referencia | null;
  modulo: Referencia | null;
  conocimiento: ConocimientoVinculado | null;
  solucion: string | null;
  fechaResolucion: string | null;
  fechaCierre: string | null;
  comentarios: ComentarioTicket[];
  historial: HistorialEstado[];
  adjuntos: AdjuntoTicket[];
}

export type TipoAdjunto = 'IMAGEN' | 'VIDEO' | 'DOCUMENTO';

/** Imagen, video o PDF subido al ticket. Con comentarioId va dentro de ese mensaje. */
export interface AdjuntoTicket {
  id: number;
  nombre: string;
  mime: string;
  tipo: TipoAdjunto;
  tamano: number;
  comentarioId: number | null;
  autor: Referencia | null;
  fecha: string;
}

/** Límites de archivos (los mismos que valida el servidor). */
export const ADJUNTO_MAX_BYTES = 25 * 1024 * 1024;
export const ADJUNTO_ACEPTA = 'image/png,image/jpeg,image/gif,image/webp,video/mp4,video/quicktime,video/webm,application/pdf';

export interface ConteoTickets {
  porEstado: Record<EstadoTicket, number>;
  total: number;
  criticosActivos: number;
  sinTecnico: number;
}

export interface PaginaTickets {
  contenido: TicketResumen[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
}

export interface FiltroTickets {
  estado?: EstadoTicket | null;
  prioridad?: PrioridadTicket | null;
  etiquetaId?: number | null;
  asignadoId?: number | null;
  texto?: string | null;
  page?: number;
  size?: number;
}

export interface CrearTicketRequest {
  titulo: string;
  descripcion: string;
  prioridad: PrioridadTicket;
  hardwareId: number | null;
  sistemaId: number | null;
  moduloId: number | null;
}

export interface ConocimientoSugerido {
  id: number;
  titulo: string;
  hardware: string | null;
  sistema: string | null;
  modulo: string | null;
}

export interface EtiquetaRequest {
  nombre: string;
  color: string;
  nivel?: NivelEtiqueta;
}

export const ESTADOS_TICKET: readonly EstadoTicket[] = [
  'ABIERTO',
  'EN_PROGRESO',
  'EN_ESPERA',
  'RESUELTO',
  'CERRADO',
  'CANCELADO',
];

export const PRIORIDADES_TICKET: readonly PrioridadTicket[] = ['BAJA', 'MEDIA', 'ALTA', 'CRITICA'];

/** Etiqueta visible y clase CSS de cada estado (badge). */
export const ESTADO_INFO: Record<EstadoTicket, { label: string; clase: string }> = {
  ABIERTO: { label: 'Abierto', clase: 'est-abierto' },
  EN_PROGRESO: { label: 'En progreso', clase: 'est-progreso' },
  EN_ESPERA: { label: 'En espera', clase: 'est-espera' },
  RESUELTO: { label: 'Resuelto', clase: 'est-resuelto' },
  CERRADO: { label: 'Cerrado', clase: 'est-cerrado' },
  CANCELADO: { label: 'Cancelado', clase: 'est-cancelado' },
};

export const PRIORIDAD_INFO: Record<PrioridadTicket, { label: string; clase: string; ayuda: string }> = {
  BAJA: { label: 'Baja', clase: 'pri-baja', ayuda: 'Molesta, pero puedo seguir trabajando' },
  MEDIA: { label: 'Media', clase: 'pri-media', ayuda: 'Me retrasa en parte del trabajo' },
  ALTA: { label: 'Alta', clase: 'pri-alta', ayuda: 'No puedo hacer una tarea importante' },
  CRITICA: { label: 'Crítica', clase: 'pri-critica', ayuda: 'Afecta a la sucursal o a muchas personas' },
};

export function esFinal(estado: EstadoTicket): boolean {
  return estado === 'CERRADO' || estado === 'CANCELADO';
}

/** Texto negro o blanco según el color de fondo de la etiqueta. */
export function colorTextoEtiqueta(hex: string): string {
  const limpio = (hex || '').replace('#', '');
  if (limpio.length !== 6) {
    return '#ffffff';
  }
  const r = parseInt(limpio.slice(0, 2), 16);
  const g = parseInt(limpio.slice(2, 4), 16);
  const b = parseInt(limpio.slice(4, 6), 16);
  const luminancia = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminancia > 0.62 ? '#0f172a' : '#ffffff';
}

const FORMATO_FECHA = new Intl.DateTimeFormat('es-CL', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

const FORMATO_DIA = new Intl.DateTimeFormat('es-CL', { day: '2-digit', month: 'short', year: 'numeric' });

export function fechaLarga(valor: string | null | undefined): string {
  const fecha = valor ? new Date(valor) : null;
  return fecha && !isNaN(fecha.getTime()) ? FORMATO_FECHA.format(fecha) : '—';
}

export function fechaCorta(valor: string | null | undefined): string {
  const fecha = valor ? new Date(valor) : null;
  return fecha && !isNaN(fecha.getTime()) ? FORMATO_DIA.format(fecha) : '—';
}

/** "hace 5 min", "hace 3 h", "hace 2 días"; más de una semana muestra la fecha. */
export function fechaRelativa(valor: string | null | undefined): string {
  const fecha = valor ? new Date(valor) : null;
  if (!fecha || isNaN(fecha.getTime())) {
    return '—';
  }
  const minutos = Math.round((Date.now() - fecha.getTime()) / 60000);
  if (minutos < 1) return 'recién';
  if (minutos < 60) return `hace ${minutos} min`;
  const horas = Math.round(minutos / 60);
  if (horas < 24) return `hace ${horas} h`;
  const dias = Math.round(horas / 24);
  if (dias < 7) return `hace ${dias} día${dias === 1 ? '' : 's'}`;
  return fechaCorta(valor);
}
