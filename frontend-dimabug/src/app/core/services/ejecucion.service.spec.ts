import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { EjecucionService } from './ejecucion.service';

describe('EjecucionService: consultas frescas y notas parciales', () => {
  let api: EjecucionService;
  let http: HttpTestingController;
  const id = 98001;
  const key = `dimabug.ejecuciones.lista.v1.pasos.${id}`;
  beforeEach(() => {
    localStorage.removeItem(key);
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    api = TestBed.inject(EjecucionService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); localStorage.removeItem(key); });

  it('procesa HTTP 201 y conserva el ID creado para navegar sin repetir el POST', () => {
    const listaKey = 'dimabug.ejecuciones.lista.v1';
    const anterior = localStorage.getItem(listaKey);
    try {
      let creadoId: number | undefined;
      api.iniciar(1).subscribe(e => { creadoId = e.id; });
      const req = http.expectOne('/api/procedimientos/1/ejecuciones');
      expect(req.request.method).toBe('POST');
      req.flush({ id: 14, procedimientoId: 1, estado: 'EN_CURSO', fechaInicio: '2026-11-07T23:46:29.6827125', fechaFin: null }, { status: 201, statusText: 'Created' });
      expect(creadoId).toBe(14);
      http.expectNone('/api/procedimientos/1/ejecuciones');
    } finally {
      if (anterior === null) localStorage.removeItem(listaKey); else localStorage.setItem(listaKey, anterior);
    }
  });

  it('force consulta HTTP aunque exista shareReplay o localStorage', () => {
    api.obtenerPasos(id).subscribe();
    http.expectOne(`/api/ejecuciones/${id}/pasos`).flush([{ ejecucionPasoId: 1, cumplido: false, version: 1 }]);
    const recibidos: number[] = [];
    api.obtenerPasos(id, true).subscribe((pasos) => recibidos.push(pasos[0].version!));
    expect(recibidos).toEqual([]);
    http.expectOne(`/api/ejecuciones/${id}/pasos`).flush([{ ejecucionPasoId: 1, cumplido: true, version: 2 }]);
    expect(recibidos).toEqual([2]);
  });
  it('la nota parcial se serializa sin cumplido y conserva la versión esperada', () => {
    api.actualizarPaso(id, 1, { observacion: ' Nota ', versionEsperada: 9 }).subscribe();
    const req = http.expectOne(`/api/ejecuciones/${id}/pasos/1`);
    expect(JSON.parse(JSON.stringify(req.request.body))).toEqual({ observacion: 'Nota', versionEsperada: 9 });
    req.flush({ ejecucionPasoId: 1, cumplido: true, estado: 'COMPLETADO', version: 10 });
  });
  it('los estados externos y la criticidad se mantienen separados', () => {
    api.obtenerPasos(id, true).subscribe((pasos) => {
      expect(pasos[0].modoEjecucion).toBe('VALIDACION_EXTERNA'); expect(pasos[0].esCritico).toBeTrue();
      expect(pasos[0].estado).toBe('EN_VALIDACION'); expect(pasos[0].cumplido).toBeFalse();
    });
    http.expectOne(`/api/ejecuciones/${id}/pasos`).flush([{ ejecucionPasoId: 1, esCritico: true, modoEjecucion: 'VALIDACION_EXTERNA', estado: 'EN_VALIDACION', cumplido: false }]);
  });
  it('una lectura lenta cancela el HTTP a los 15 segundos y permite una consulta fresca',fakeAsync(()=>{
    let mensaje='';api.obtenerPasos(id,true).subscribe({error:e=>mensaje=e.message});const pendiente=http.expectOne(`/api/ejecuciones/${id}/pasos`);tick(15000);expect(pendiente.cancelled).toBeTrue();expect(mensaje).toContain('tardó demasiado');api.obtenerPasos(id,true).subscribe(p=>expect(p.length).toBe(0));http.expectOne(`/api/ejecuciones/${id}/pasos`).flush([]);
  }));
  it('al abandonar una lectura compartida se cancela la petición subyacente',()=>{
    const sub=api.obtener(id,true).subscribe();const pendiente=http.expectOne(`/api/ejecuciones/${id}`);sub.unsubscribe();expect(pendiente.cancelled).toBeTrue();
  });

});
