import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AdminSucursalesComponent } from './admin-sucursales.component';
import { AdminSucursalesApiService, SucursalAdmin } from '../../core/services/admin-sucursales-api.service';

describe('Administración de sucursales', () => {
  let c: AdminSucursalesComponent;
  let api: jasmine.SpyObj<AdminSucursalesApiService>;
  const sucursal: SucursalAdmin = { id: 1, codigo: 'QA_01', nombre: 'Sintética', responsableNombre: 'QA', responsableEmail: 'qa-no-enviar@dimarsa.cl', activa: true, version: 2 };
  beforeEach(() => {
    api = jasmine.createSpyObj('sucursales', ['listar', 'crear', 'editar', 'estado']);
    api.listar.and.returnValue(of([sucursal]));
    TestBed.configureTestingModule({ providers: [{ provide: AdminSucursalesApiService, useValue: api }] });
    c = TestBed.runInInjectionContext(() => new AdminSucursalesComponent()); c.ngOnInit();
  });
  it('el error de edición conserva el formulario y su versión para revisión', () => {
    c.editar(sucursal); c.datos.nombre = 'Cambio'; api.editar.and.returnValue(throwError(() => new Error('Recarga antes de guardar')));
    c.guardar(); expect(c.datos.nombre).toBe('Cambio'); expect(c.editando?.version).toBe(2); expect(c.error).toContain('Recarga'); expect(c.guardando).toBeFalse();
  });
  it('un dominio parecido o una lista no se envían al backend', () => {
    c.editar(sucursal); c.datos.responsableEmail = 'a@dimarsa.cl.evil'; c.guardar(); expect(api.editar).not.toHaveBeenCalled();
    c.datos.responsableEmail = 'a@dimarsa.cl,b@dimarsa.cl'; c.guardar(); expect(api.editar).not.toHaveBeenCalled();
  });
  it('editar exige conservar la versión observada y no cambiar el estado', () => {
    c.editar(sucursal); c.datos.nombre = 'Cambio'; api.editar.and.returnValue(of({ ...sucursal, nombre: 'Cambio', version: 3 })); c.guardar();
    expect(api.editar.calls.mostRecent().args[2]).toBe(2); expect(c.editando).toBeNull(); expect(c.sucursales[0].nombre).toBe('Cambio'); expect(c.tieneCambiosPendientes()).toBeFalse();
  });
  it('actualizar la lista no cambia silenciosamente la versión del formulario', () => {
    c.editar(sucursal); api.listar.and.returnValue(of([{ ...sucursal, version: 3 }])); c.cargar(); expect(c.editando?.version).toBe(2);
  });
});
