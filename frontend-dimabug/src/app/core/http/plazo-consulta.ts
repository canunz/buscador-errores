import { MonoTypeOperatorFunction, timeout, TimeoutError } from 'rxjs';

export const PLAZO_CONSULTA_MS = 15_000;
export const MENSAJE_PLAZO_CONSULTA = 'La consulta tardó demasiado. Comprueba la conexión e intenta nuevamente.';

/** Solo lecturas: cancelar o agotar el plazo nunca reenvía una mutación. */
export function plazoConsulta<T>(): MonoTypeOperatorFunction<T> {
  return timeout({ first: PLAZO_CONSULTA_MS });
}
export function mensajePlazo(error: unknown, fallback: string): string {
  return error instanceof TimeoutError ? MENSAJE_PLAZO_CONSULTA : error instanceof Error ? error.message : fallback;
}
