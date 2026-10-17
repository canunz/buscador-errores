import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AdminOrganizacionApiService } from './admin-organizacion-api.service';
import { OrganizacionService } from './organizacion.service';
describe('Administración persistente de organización', () => {
  let api: AdminOrganizacionApiService; let http: HttpTestingController; let invalidar: jasmine.Spy;
  beforeEach(() => { invalidar = jasmine.createSpy(); TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(),
    { provide: OrganizacionService, useValue: { invalidarDepartamentos: invalidar } }] });
    api = TestBed.inject(AdminOrganizacionApiService); http = TestBed.inject(HttpTestingController); });
  afterEach(() => http.verify());
  it('consulta administrativos incluidos inactivos', () => { api.listar().subscribe(d => expect(d[0].activo).toBeFalse());
    http.expectOne(r => r.url.endsWith('/admin/departamentos')).flush([{ id: 1, nombre: 'TI', activo: false, version: 2 }]); expect(invalidar).not.toHaveBeenCalled(); });
  it('crea por POST y actualiza cache únicamente después de guardar', () => { api.guardar(null, 'TI').subscribe();
    const r = http.expectOne(r => r.method === 'POST'); expect(r.request.body).toEqual({ nombre: 'TI' }); expect(invalidar).not.toHaveBeenCalled(); r.flush({}); expect(invalidar).toHaveBeenCalled(); });
  it('edita nombre con versión sin estado', () => { api.guardar(1, 'TI', 3).subscribe(); const r = http.expectOne(r => r.method === 'PUT'); expect(r.request.body).toEqual({ nombre: 'TI', version: 3 }); r.flush({}); });
  it('propaga conflicto y conserva cache', () => { let mensaje = ''; api.estado(1, false, 2).subscribe({ error: e => mensaje = e.message });
    const r = http.expectOne(r => r.method === 'PATCH'); expect(r.request.body).toEqual({ activo: false, version: 2 }); r.flush({ message: 'Recarga la versión' }, { status: 409, statusText: 'Conflict' });
    expect(mensaje).toContain('Recarga'); expect(invalidar).not.toHaveBeenCalled(); });
  it('edita contacto sin trasladarlo ni sobrescribir estado', () => { api.guardarContacto(1, 4, { tipo: 'Anexo', valor: '619', activo: false, version: 3 }).subscribe();
    const r = http.expectOne(r => r.method === 'PUT' && r.url.endsWith('/1/contactos/4')); expect(r.request.body).toEqual({ tipo: 'Anexo', valor: '619', version: 3 }); r.flush({}); });
  it('consulta contactos administrativos', () => { api.contactos(1).subscribe(); http.expectOne(r => r.url.endsWith('/1/contactos')).flush([]); });
  it('crea contacto con estado individual', () => { api.guardarContacto(1, null, { tipo: 'Correo', valor: 'a@b.cl', activo: false }).subscribe();
    const r = http.expectOne(r => r.method === 'POST'); expect(r.request.body.activo).toBeFalse(); r.flush({}); });
  it('reactiva contacto con versión', () => { api.estadoContacto(1, 4, true, 3).subscribe(); const r = http.expectOne(r => r.url.endsWith('/1/contactos/4/estado'));
    expect(r.request.body).toEqual({ activo: true, version: 3 }); r.flush({}); });
});
