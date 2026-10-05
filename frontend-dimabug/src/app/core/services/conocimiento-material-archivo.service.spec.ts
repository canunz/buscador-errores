import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ConocimientoService } from './conocimiento.service';

describe('ConocimientoService materiales archivo', () => {
  let service: ConocimientoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(ConocimientoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('crea enlace JSON existente', () => {
    const body = { nombre: 'Manual', tipo: 'ENLACE' as const, url: 'https://ejemplo.cl' };
    service.crearMaterial(4, body).subscribe((item) => {
      expect(item.nombre).toBe('Manual');
      expect(item.url).toBe('https://ejemplo.cl');
    });
    const req = http.expectOne('/api/conocimientos/4/materiales');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    expect(req.request.headers.has('Content-Type') ? req.request.headers.get('Content-Type') : '').not.toContain(
      'multipart',
    );
    req.flush({ id: 1, ...body });
  });

  it('sube PDF e imagen con FormData sin Content-Type multipart manual', () => {
    const pdf = new File([new Uint8Array([1])], 'a.pdf', { type: 'application/pdf' });
    service.crearMaterialArchivo(4, 'Guía', 'PDF', pdf).subscribe((item) => {
      expect(item.id).toBe(9);
      expect(item.url).toBe('/api/conocimientos/4/materiales/9/archivo');
    });
    const req = http.expectOne('/api/conocimientos/4/materiales/archivo');
    expect(req.request.body instanceof FormData).toBeTrue();
    expect(req.request.headers.get('Content-Type')).toBeNull();
    const data = req.request.body as FormData;
    expect(data.get('nombre')).toBe('Guía');
    expect(data.get('tipo')).toBe('PDF');
    const adjunto = data.get('archivo') as File;
    expect(adjunto).toBeInstanceOf(File);
    expect(adjunto.name).toBe('a.pdf');
    req.flush({
      id: 9,
      nombre: 'Guía',
      tipo: 'PDF',
      url: '/api/conocimientos/4/materiales/9/archivo',
    });
  });

  it('descarga con blob y actualiza listado tras upload', () => {
    const file = new File([new Uint8Array([1])], 'a.png', { type: 'image/png' });
    let listado: string[] = [];
    service.crearMaterialArchivo(4, 'Foto', 'IMAGEN', file).subscribe();
    http.expectOne('/api/conocimientos/4/materiales/archivo').flush({
      id: 2,
      nombre: 'Foto',
      tipo: 'IMAGEN',
      url: '/api/conocimientos/4/materiales/2/archivo',
    });
    service.listarMateriales(4, true).subscribe((items) => {
      listado = items.map((i) => i.nombre);
    });
    http.expectOne('/api/conocimientos/4/materiales').flush([
      { id: 2, nombre: 'Foto', tipo: 'IMAGEN', url: '/api/conocimientos/4/materiales/2/archivo' },
    ]);
    expect(listado).toEqual(['Foto']);

    spyOn(URL, 'createObjectURL').and.returnValue('blob:x');
    spyOn(URL, 'revokeObjectURL');
    spyOn(document.body, 'appendChild');
    spyOn(document, 'createElement').and.returnValue({
      href: '',
      download: '',
      rel: '',
      click() {},
      remove() {},
    } as unknown as HTMLAnchorElement);

    service
      .descargarMaterial({
        id: 2,
        nombre: 'Foto',
        tipo: 'IMAGEN',
        url: '/api/conocimientos/4/materiales/2/archivo',
      })
      .subscribe();
    const down = http.expectOne('/api/conocimientos/4/materiales/2/archivo');
    expect(down.request.responseType).toBe('blob');
    down.flush(new Blob(['x'], { type: 'image/png' }));
  });

  it('propaga error comprensible si falla el upload', () => {
    const file = new File([new Uint8Array([1])], 'a.pdf', { type: 'application/pdf' });
    let mensaje = '';
    service.crearMaterialArchivo(4, 'X', 'PDF', file).subscribe({
      error: (err: Error) => (mensaje = err.message),
    });
    http.expectOne('/api/conocimientos/4/materiales/archivo').flush(
      { message: 'El archivo supera 10 MB' },
      { status: 413, statusText: 'Payload Too Large' },
    );
    expect(mensaje).toContain('supera 10 MB');
  });
});
