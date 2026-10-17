import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ConocimientoService } from './conocimiento.service';

describe('QA-009 API persistente de soluciones', () => {
  let api: ConocimientoService;
  let http: HttpTestingController;
  const solucion = { id: 8, titulo: 'Reiniciar servicio', descripcion: 'Paso uno\nPaso dos', tipo: 'PASOS', orden: 1, activo: true, autoservicio: false };
  beforeEach(() => {
    localStorage.clear(); sessionStorage.clear();
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    api = TestBed.inject(ConocimientoService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('desactiva por PATCH e invalida el detalle guardado', () => {
    api.listarSoluciones(4).subscribe(); http.expectOne('/api/conocimientos/4/soluciones').flush([solucion]);
    expect(api.leerSeccion('soluciones:4')).not.toBeNull();
    api.cambiarEstadoSolucion(4, 8, false).subscribe(s => expect(s.activo).toBeFalse());
    const req = http.expectOne('/api/admin/conocimientos/4/soluciones/8/estado');
    expect(req.request.method).toBe('PATCH'); expect(req.request.body).toEqual({ activo: false });
    req.flush({ ...solucion, activo: false }); expect(api.leerSeccion('soluciones:4')).toBeNull();
    api.listarSoluciones(4).subscribe(items => expect(items).toEqual([]));
    http.expectOne('/api/conocimientos/4/soluciones').flush([]);
  });
  it('reactiva y cambia autoservicio en contratos separados', () => {
    api.cambiarEstadoSolucion(4, 8, true).subscribe();
    const estado = http.expectOne('/api/admin/conocimientos/4/soluciones/8/estado');
    expect(estado.request.body).toEqual({ activo: true }); estado.flush(solucion);
    api.cambiarAutoservicioSolucion(4, 8, true).subscribe();
    const auto = http.expectOne('/api/admin/conocimientos/4/soluciones/8/autoservicio');
    expect(auto.request.body).toEqual({ autoservicio: true }); auto.flush({ ...solucion, autoservicio: true });
  });
  it('propaga el mensaje RN-08 y conserva datos cuando el PATCH falla', () => {
    api.listarSoluciones(4).subscribe(); http.expectOne('/api/conocimientos/4/soluciones').flush([solucion]);
    api.cambiarEstadoSolucion(4, 8, false).subscribe({ error: err => expect(err.message).toContain('RN-08') });
    http.expectOne('/api/admin/conocimientos/4/soluciones/8/estado')
      .flush({ message: 'RN-08: última solución activa' }, { status: 409, statusText: 'Conflict' });
    expect(api.leerSeccion('soluciones:4')).not.toBeNull();
  });
  it('ignora las antiguas soluciones ocultas en sessionStorage', () => {
    sessionStorage.setItem('dimabug.soluciones.ocultas.v1', JSON.stringify(['4-8']));
    api.listarSolucionesCatalogo(true).subscribe(items => { expect(items.length).toBe(1); expect(items[0].nombre).toBe(solucion.titulo); });
    http.expectOne('/api/conocimientos').flush([{ id: 4, titulo: 'Caso', estado: 'PUBLICADO' }]);
    http.expectOne('/api/conocimientos/4/soluciones').flush([solucion]);
    expect(sessionStorage.getItem('dimabug.soluciones.ocultas.v1')).toBe('["4-8"]');
  });
  it('administración incluye inactivas y mantiene título y descripción separados', () => {
    api.listarSolucionesCatalogo(true, true).subscribe(items => {
      expect(items[0].activo).toBeFalse(); expect(items[0].nombre).toBe(solucion.titulo);
      expect(items[0].descripcion).toBe(solucion.descripcion);
    });
    http.expectOne('/api/conocimientos').flush([{ id: 4, titulo: 'Caso' }]);
    http.expectOne('/api/admin/conocimientos/4/soluciones').flush([{ ...solucion, activo: false }]);
  });
  it('compatible con soluciones antiguas sin título ni indicadores', () => {
    api.listarSoluciones(4).subscribe(items => {
      expect(items[0].titulo).toBeNull(); expect(items[0].activo).toBeTrue(); expect(items[0].autoservicio).toBeFalse();
    });
    http.expectOne('/api/conocimientos/4/soluciones').flush([{ id: 8, descripcion: 'Contenido', tipo: 'PASOS', orden: 1 }]);
  });
  it('no ofrece inactivas y consulta el servidor incluso con caché anterior', () => {
    api.listarSoluciones(4).subscribe(); http.expectOne('/api/conocimientos/4/soluciones').flush([solucion]);
    api.listarSoluciones(4).subscribe(items => expect(items).toEqual([]));
    http.expectOne('/api/conocimientos/4/soluciones').flush([{ ...solucion, activo: false }]);
  });
  it('POST y PUT solo transmiten los atributos operacionales', () => {
    const datos = { titulo: 'Título', descripcion: 'D', tipo: 'PASOS' as const, orden: 1 };
    api.crearSolucion(4, datos).subscribe(); const post = http.expectOne('/api/conocimientos/4/soluciones');
    expect(post.request.body).toEqual(datos); post.flush(solucion);
    api.modificarSolucion(4, 8, datos).subscribe(); const put = http.expectOne('/api/conocimientos/4/soluciones/8');
    expect(put.request.body).toEqual(datos); put.flush(solucion);
  });
});
