import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, provideRouter } from '@angular/router';
import { adminGuard, staffGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';
import { esAdministrador } from '../models/usuario.model';
describe('Acceso administrativo y operacional', () => {
  const estado = { autenticado: true, admin: false, staff: true };
  beforeEach(() => {
    estado.autenticado = true; estado.admin = false; estado.staff = true;
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: {
      isAuthenticated: () => estado.autenticado, isAdmin: () => estado.admin, isStaff: () => estado.staff,
    } }] });
  });
  const ejecutar = (guard: typeof adminGuard) => TestBed.runInInjectionContext(() => guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
  it('administrador autenticado accede', () => { estado.admin = true; expect(ejecutar(adminGuard)).toBeTrue(); });
  it('staff conserva operaciones pero no administración', () => {
    expect(ejecutar(staffGuard)).toBeTrue();
    expect(TestBed.inject(Router).serializeUrl(ejecutar(adminGuard) as any)).toBe('/inicio');
  });
  it('sin sesión redirige al login', () => { estado.autenticado = false; expect(TestBed.inject(Router).serializeUrl(ejecutar(adminGuard) as any)).toBe('/login'); });
  it('solo el nombre canónico concede administración', () => {
    expect(esAdministrador({ rolNombre: 'ADMINISTRADOR' } as any)).toBeTrue();
    expect(esAdministrador({ rolNombre: 'ADMIN' } as any)).toBeFalse();
  });
});
