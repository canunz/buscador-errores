import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminSucursalesApiService, DatosSucursal } from './admin-sucursales-api.service';

describe('AdminSucursalesApiService', () => {
  let api: AdminSucursalesApiService;
  let http: HttpTestingController;
  const datos: DatosSucursal = { codigo: 'QA_01', nombre: 'Sintética', responsableNombre: 'QA', responsableEmail: 'qa-no-enviar@dimarsa.cl' };
  beforeEach(() => { TestBed.configureTestingModule({ imports: [HttpClientTestingModule] }); api = TestBed.inject(AdminSucursalesApiService); http = TestBed.inject(HttpTestingController); });
  afterEach(() => http.verify());
  it('edita con la versión leída, sin activaciones implícitas', () => {
    api.editar(1, datos, 3).subscribe();
    const req = http.expectOne('/api/admin/sucursales/1'); expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ ...datos, version: 3 }); req.flush({ id: 1, ...datos, activa: false, version: 4 });
  });
  it('usa desactivación versionada y conserva el registro', () => {
    api.estado({ id: 1, ...datos, activa: true, version: 4 }, false).subscribe();
    const req = http.expectOne('/api/admin/sucursales/1/estado'); expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ activa: false, version: 4 }); req.flush({ id: 1, ...datos, activa: false, version: 5 });
  });
  it('un conflicto informa al administrador sin reintentar ni sobrescribir', () => {
    let error = '';
    api.editar(1, datos, 3).subscribe({ error: (e: Error) => error = e.message });
    http.expectOne('/api/admin/sucursales/1').flush({ message: 'Recarga: versión obsoleta' }, { status: 409, statusText: 'Conflict' });
    expect(error).toContain('versión obsoleta'); http.expectNone('/api/admin/sucursales/1');
  });
});
