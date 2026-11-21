/**
 * Tipos de datos de ocr que usa el frontend.
 * Deben coincidir con lo que responde el backend.
 */
export type EstadoOcr = 'inicial' | 'seleccionada' | 'procesando' | 'reconocido' | 'sin_texto' | 'error';
export interface ResultadoOcr { texto: string; confianza: number; }
export type RespuestaWorkerOcr =
  | { tipo: 'progreso'; progreso: number }
  | { tipo: 'resultado'; texto: string; confianza: number }
  | { tipo: 'error' };
export { LIMITES_OCR } from '../ocr-compartido/limites';
