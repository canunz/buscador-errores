import { inject } from '@angular/core';
import { CanActivateFn, CanActivateChildFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Usuarios con acceso básico solo pueden estar en /solicitudes. */
export const alcanceBasicoGuard: CanActivateChildFn = (_route, state) =>
  !inject(AuthService).accesoBasico() || state.url.split('?')[0] === '/solicitudes'
    || inject(Router).createUrlTree(['/solicitudes']);

/** Deja entrar solo si hay sesión; si no, manda al login. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/login']);
};

/** Solo el administrador. */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated() && auth.isAdmin()) {
    return true;
  }
  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }
  return router.createUrlTree([auth.accesoBasico() ? '/solicitudes' : '/inicio']);
};

/** Solo Soporte y administrador (staff). */
export const staffGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }
  if (auth.isStaff()) {
    return true;
  }
  return router.createUrlTree([auth.accesoBasico() ? '/solicitudes' : '/inicio']);
};

/** Para el login: si ya tiene sesión, lo manda al inicio. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree([auth.accesoBasico() ? '/solicitudes' : '/inicio']);
};
