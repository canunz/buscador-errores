import { HttpErrorResponse } from '@angular/common/http';

export function mensajeApiError(err: HttpErrorResponse, fallback: string): string {
  if (err.status === 400) {
    return mensajeSeguro(err, 'Los datos o la clasificación no son válidos.');
  }
  if (err.status === 401) {
    return 'No se pudo completar la consulta. Vuelve a intentar o inicia sesión de nuevo.';
  }
  if (err.status === 403) {
    return 'Tu perfil no puede hacer esta acción. Como consulta solo ves las guías publicadas.';
  }
  if (err.status === 404) {
    return fallback || 'No se encontró el recurso solicitado.';
  }
  if (err.status === 409) {
    return mensajeSeguro(err, 'La asociación ya existe o hay un conflicto con los datos enviados.');
  }
  if (err.status === 405) {
    return 'El servidor todavía no acepta eliminar este material. Reinicie el backend y vuelva a intentar.';
  }
  if (err.status === 0) {
    return 'No se pudo conectar con el servidor. Intente más tarde.';
  }
  if (err.status >= 500) {
    return 'Ocurrió un error inesperado. Intente más tarde.';
  }
  return mensajeSeguro(err, fallback);
}

function mensajeSeguro(err: HttpErrorResponse, fallback: string): string {
  const raw = extraerMensaje(err);
  if (typeof raw !== 'string') {
    return fallback;
  }
  const msg = raw.trim();
  if (!msg || msg.length > 160) {
    return fallback;
  }
  const tecnico =
    /spring|boot|:8080|:8081|localhost|\/api\/|httpd|apache|proxy|endpoint|stack|exception|nullpointer|^not found$|^unauthorized$|^forbidden$/i;
  return tecnico.test(msg) ? fallback : msg;
}

function extraerMensaje(err: HttpErrorResponse): unknown {
  if (typeof err.error === 'string') {
    return err.error;
  }
  if (err.error && typeof err.error === 'object') {
    return err.error.message || err.error.error;
  }
  return undefined;
}
