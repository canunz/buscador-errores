import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ProcedimientoService } from './procedimiento.service';

describe('ProcedimientoService materiales archivo', () => {
  let service: ProcedimientoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(ProcedimientoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('mantiene creación de enlace de paso', () => {
    service.crearMaterial(1, 5, { nombre: 'Guía', tipo: 'PDF', url: 'https://x' }).subscribe((m) => {
      expect(m.url).toBe('https://x');
    });
    const req = http.expectOne('/api/procedimientos/1/pasos/5/materiales');
    expect(req.request.body).toEqual({ nombre: 'Guía', tipo: 'PDF', url: 'https://x' });
    req.flush({ id: 8, nombre: 'Guía', tipo: 'PDF', url: 'https://x' });
  });

  it('sube archivo de paso con FormData correcto', () => {
    const pdf = new File([new Uint8Array([1])], 'p.pdf', { type: 'application/pdf' });
    service.crearMaterialArchivo(1, 5, 'Guía', 'PDF', pdf).subscribe();
    const req = http.expectOne('/api/procedimientos/1/pasos/5/materiales/archivo');
    expect(req.request.body instanceof FormData).toBeTrue();
    expect(req.request.headers.get('Content-Type')).toBeNull();
    req.flush({
      id: 8,
      nombre: 'Guía',
      tipo: 'PDF',
      url: '/api/procedimientos/1/pasos/5/materiales/8/archivo',
    });
  });
});
