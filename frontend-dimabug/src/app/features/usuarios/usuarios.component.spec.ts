import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { throwError } from 'rxjs';
import { UsuariosComponent } from './usuarios.component';
import { UsuarioService } from '../../core/services/usuario.service';
import { AuthService } from '../../core/services/auth.service';
describe('Usuarios: rechazo de cambio de estado', () => {
  it('no anuncia éxito ni intenta PUT si el servidor protege al último administrador', () => {
    const api = jasmine.createSpyObj('usuarios', ['listaEnSesion', 'rolesEnSesion', 'cambiarEstado', 'actualizar']);
    api.listaEnSesion.and.returnValue([]); api.rolesEnSesion.and.returnValue([]);
    api.cambiarEstado.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Debe conservar al menos un administrador activo' } })));
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: UsuarioService, useValue: api },
      { provide: AuthService, useValue: { usuario: () => ({ usuarioId: 2, usuarioEmail: 'otro@x.cl' }) } }] });
    const c = TestBed.createComponent(UsuariosComponent).componentInstance;
    c.toggleEstado({ usuarioId: 1, usuarioEmail: 'admin@x.cl', usuarioNombre: 'Admin', usuarioEstado: true });
    c.aceptarConfirmacion();
    expect(c.error).toContain('administrador activo'); expect(c.success).toBe('');
    expect(api.actualizar).not.toHaveBeenCalled(); expect(c.cambiandoEstado).toBeFalse();
  });
});
