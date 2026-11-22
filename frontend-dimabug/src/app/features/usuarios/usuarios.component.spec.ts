import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
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


describe('Usuarios: alta sin contraseña', () => {
  it('crea una cuenta personal sin pedir ni enviar una contraseña', () => {
    const api = jasmine.createSpyObj('usuarios', ['listaEnSesion', 'rolesEnSesion', 'crear']);
    api.listaEnSesion.and.returnValue([]); api.rolesEnSesion.and.returnValue([]);
    api.crear.and.returnValue(of({ usuarioId: 4 }));
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: UsuarioService, useValue: api },
      { provide: AuthService, useValue: { usuario: () => ({ usuarioId: 2, usuarioEmail: 'admin@example.invalid' }) } }] });
    const c = TestBed.createComponent(UsuariosComponent).componentInstance;
    spyOn(c, 'cargar'); c.openCreate();
    c.form.patchValue({ nombre: 'Persona QA', email: 'persona@gmail.com', rolId: 1 });
    expect(c.form.valid).toBeTrue(); c.save();
    expect(api.crear).toHaveBeenCalledOnceWith(jasmine.objectContaining({ usuarioEmail: 'persona@gmail.com' }));
    expect(api.crear.calls.mostRecent().args[0].usuarioPassword).toBeUndefined();
    expect(c.error).toBe(''); expect(c.success).toBe('Usuario creado.');
    expect(c.haceCuanto()).toBe('Nunca ha ingresado');
  });
});
