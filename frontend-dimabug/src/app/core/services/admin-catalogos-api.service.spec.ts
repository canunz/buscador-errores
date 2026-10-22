import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AdminCatalogosApiService, CatalogoAdminRequest } from './admin-catalogos-api.service';
import { ClasificacionService } from './clasificacion.service';
describe('API de catálogos administrativos', () => {
  let api: AdminCatalogosApiService;
  let http: HttpTestingController;
  let clasificacion: ClasificacionService;
  const datos: CatalogoAdminRequest = { nombre: 'POS', activo: true, sistemaIds: [2], sistemaId: null, orden: null, descripcion: null, sistemaOperativo: 'Windows' };
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    api = TestBed.inject(AdminCatalogosApiService); http = TestBed.inject(HttpTestingController);
    clasificacion = TestBed.inject(ClasificacionService); clasificacion.invalidarCatalogos();
  });
  afterEach(() => http.verify());
  it('consulta Hardware completo por ID para conservar asociaciones inactivas', () => {
    const invalidar = spyOn(clasificacion, 'invalidarCatalogos');
    api.obtener('hardware', 7).subscribe(item => expect(item.sistemaIds).toEqual([2, 3]));
    const req = http.expectOne('/api/admin/catalogos/hardware/7');
    expect(req.request.method).toBe('GET'); req.flush({ id: 7, ...datos, sistemaIds: [2, 3] });
    expect(invalidar).not.toHaveBeenCalled();
  });
  it('alta invalida clasificación y siguiente lectura consulta red', () => {
    clasificacion.listarHardware().subscribe(); http.expectOne('/api/hardware').flush([{ id: 1, nombre: 'Anterior' }]);
    api.guardar('hardware', null, datos).subscribe(); const alta = http.expectOne('/api/admin/catalogos/hardware');
    expect(alta.request.method).toBe('POST'); expect(alta.request.body).toEqual(datos); alta.flush({ id: 3, ...datos });
    expect(clasificacion.snapshotHardware()).toEqual([]);
    clasificacion.listarHardware().subscribe(); http.expectOne('/api/hardware').flush([{ id: 3, nombre: 'POS' }]);
  });
  it('edita y cambia estado mediante PUT y PATCH', () => {
    api.guardar('sistemas', 2, datos).subscribe(); const put = http.expectOne('/api/admin/catalogos/sistemas/2');
    expect(put.request.method).toBe('PUT'); put.flush({ id: 2, ...datos });
    api.cambiarEstado('sistemas', 2, false).subscribe(); const patch = http.expectOne('/api/admin/catalogos/sistemas/2/estado');
    expect(patch.request.body).toEqual({ activo: false }); expect(patch.request.method).toBe('PATCH'); patch.flush({ id: 2, ...datos, activo: false });
  });
  it('un servicio recreado vuelve a consultar el servidor', () => {
    api.listar('frecuencias').subscribe(); http.expectOne('/api/admin/catalogos/frecuencias').flush([]);
    const nuevo = TestBed.runInInjectionContext(() => new AdminCatalogosApiService());
    nuevo.listar('frecuencias').subscribe(items => expect(items).toEqual([])); http.expectOne('/api/admin/catalogos/frecuencias').flush([]);
  });
  it('lista vacía de hardware no bloquea una carga posterior', () => {
    clasificacion.listarHardware().subscribe(); http.expectOne('/api/hardware').flush([]);
    clasificacion.listarHardware().subscribe(); http.expectOne('/api/hardware').flush([{ id: 1, nombre: 'PC' }]);
    expect(clasificacion.snapshotHardware().length).toBe(1);
  });
  it('muestra rechazo del servidor y no invalida caché tras fallo', () => {
    const invalidar = spyOn(clasificacion, 'invalidarCatalogos');
    api.guardar('hardware', null, datos).subscribe({ error: err => expect(err.message).toContain('Duplicado') });
    http.expectOne('/api/admin/catalogos/hardware').flush({ message: 'Duplicado' }, { status: 409, statusText: 'Conflict' });
    expect(invalidar).not.toHaveBeenCalled();
  });
});
