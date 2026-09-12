import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { isPublicApiUrl } from '../config/api';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  const publicRequest = isPublicApiUrl(req.url);

  const outgoing =
    !publicRequest && token
      ? req.clone({
          setHeaders: {
            Authorization: `Bearer ${token}`,
          },
        })
      : req;

  return next(outgoing).pipe(
    catchError((err: HttpErrorResponse) => {
      if (!publicRequest && err.status === 401 && auth.token()) {
        auth.logout();
      }
      return throwError(() => err);
    }),
  );
};
