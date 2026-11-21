import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { backendApiPath, isPublicApiUrl } from '../config/api';
import { AuthService } from '../services/auth.service';

/** Evita lanzar varias comprobaciones si varias peticiones fallan juntas. */
let verificandoSesion = false;

/**
 * Interceptor: revisa todas las peticiones que salen hacia el backend.
 * Si el usuario tiene sesión, les agrega el token automáticamente.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  // Token guardado al iniciar sesión
  const token = auth.token();
  const backendPath = backendApiPath(req.url);
  const trusted = backendPath !== null;
  // Las rutas públicas (autoservicio, chatbot) no llevan token
  const publicRequest = isPublicApiUrl(req.url);

  // Copia la petición agregando "Authorization: Bearer <token>"
  const outgoing =
    trusted && !publicRequest && token
      ? req.clone({
          setHeaders: {
            Authorization: `Bearer ${token}`,
          },
        })
      : (!trusted || publicRequest) && req.headers.has('Authorization')
        ? req.clone({ headers: req.headers.delete('Authorization') })
        : req;

  // Envía la petición y, si el servidor responde 401, revisa si la sesión sigue válida
  return next(outgoing).pipe(
    catchError((err: HttpErrorResponse) => {
      const sesionRechazada = trusted && !publicRequest && err.status === 401 && !!auth.token();
      if (sesionRechazada && backendPath === '/auth/me') {
        verificandoSesion = false;
        auth.logout();
      } else if (sesionRechazada && !verificandoSesion) {
        // Un 401 al editar o eliminar no cierra la sesión si el token sigue válido.
        verificandoSesion = true;
        auth.me().subscribe({
          next: () => {
            verificandoSesion = false;
          },
          error: () => {
            verificandoSesion = false;
          },
        });
      }
      return throwError(() => err);
    }),
  );
};
