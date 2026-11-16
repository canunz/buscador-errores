import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { EjecucionChecklistComponent } from './ejecucion-checklist.component';
import { EjecucionService } from '../../core/services/ejecucion.service';
import { ProcedimientoService } from '../../core/services/procedimiento.service';
import { Ejecucion, EjecucionPaso } from '../../core/models/ejecucion.model';

describe('Checklist: persistencia antes de avanzar y guardado parcial', () => {
  let componente: EjecucionChecklistComponent;
  let respuesta: Subject<EjecucionPaso>;
  let api: jasmine.SpyObj<EjecucionService>;
  const paso = (id: number): EjecucionPaso => ({
    ejecucionPasoId: id, pasoId: id, orden: id, instruccion: 'Paso sintético', esCritico: false,
    cumplido: false, observacion: null, fecha: null, version: 7, modoEjecucion: 'MANUAL', estado: 'PENDIENTE',
  });
  const ejecucion: Ejecucion = {
    id: 20, procedimientoId: 10, estado: 'EN_CURSO', fechaInicio: null, fechaFin: null,
    observaciones: null, usuario: { id: 7, nombre: 'QA', email: 'qa@example.invalid' },
  };

  beforeEach(() => {
    respuesta = new Subject<EjecucionPaso>();
    api = jasmine.createSpyObj<EjecucionService>('ejecuciones', ['siguiente', 'actualizarPaso', 'fijarPasos', 'marcaPasos', 'obtenerPasos', 'obtener', 'detalleEnSesion', 'pasosEnSesion', 'obtenerProcedimiento']);
    api.actualizarPaso.and.returnValue(respuesta);
    api.siguiente.and.returnValue(respuesta);
    api.marcaPasos.and.returnValue(0);
    api.obtenerPasos.and.returnValue(of([paso(1), paso(2)]));
    api.obtener.and.returnValue(of({ ...ejecucion }));
    api.obtenerProcedimiento.and.returnValue(of({ id: 10, nombre: 'QA', descripcion: null, estado: 'PUBLICADO' }));
    TestBed.configureTestingModule({ providers: [
      { provide: EjecucionService, useValue: api },
      { provide: ProcedimientoService, useValue: { listar: () => of([]), listaEnSesion: () => [] } },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '20' } } } },
    ] });
    componente = TestBed.runInInjectionContext(() => new EjecucionChecklistComponent());
    componente.ejecucion = { ...ejecucion };
    componente.pasos = [paso(1), paso(2)];
  });
  afterEach(() => componente.ngOnDestroy());

  it('Siguiente persiste una sola acción y avanza tras consultar el resultado', () => {
    componente.pasos[0].modoEjecucion = 'MANUAL_GUIADO';
    componente.completarYAvanzar(componente.pasos[0]);
    componente.completarYAvanzar(componente.pasos[0]);
    expect(api.siguiente).toHaveBeenCalledTimes(1);
    expect(api.actualizarPaso).not.toHaveBeenCalled();
    expect(componente.pasoActual).toBe(1);
    api.obtenerPasos.and.returnValue(of([{ ...paso(1), cumplido: true }, paso(2)]));
    respuesta.next({ ...paso(1), cumplido: true, version: 8 });
    expect(componente.pasoActual).toBe(2);
  });
  it('Siguiente no omite una automatización pendiente', () => {
    componente.pasos[0].modoEjecucion = 'SOLICITAR_EVIDENCIA';
    componente.completarYAvanzar(componente.pasos[0]);
    expect(api.siguiente).not.toHaveBeenCalled();
  });
  it('no marca ni avanza mientras el servidor no confirma', () => {
    componente.marcar(componente.pasos[0], true);
    expect(componente.pasos[0].cumplido).toBeFalse();
    expect(componente.pasoActual).toBe(1);
    expect(componente.porcentaje).toBe(0);
    expect(api.fijarPasos).not.toHaveBeenCalled();
    expect(componente.guardando).toBe(1);
  });
  it('la aprobación externa confirmada avanza sin enviar un marcado manual', () => {
    api.obtenerPasos.and.returnValue(of([{ ...paso(1), cumplido: true }, paso(2)]));
    componente.alAutomatizacionActualizada({ avanzar: true, pasoId: 1 });
    expect(componente.pasoActual).toBe(2); expect(api.actualizarPaso).not.toHaveBeenCalled();
  });
  it('un aviso de aprobación no avanza si el servidor todavía muestra pendiente', () => {
    componente.alAutomatizacionActualizada({ avanzar: true, pasoId: 1 });
    expect(componente.pasoActual).toBe(1); expect(api.actualizarPaso).not.toHaveBeenCalled();
  });
  it('avanza solo después de la confirmación persistida', () => {
    componente.marcar(componente.pasos[0], true);
    respuesta.next({ ...paso(1), cumplido: true, estado: 'COMPLETADO', version: 8 });
    expect(componente.pasoActual).toBe(2);
    expect(componente.cumplidos).toBe(1);
    expect(componente.guardando).toBeNull();
  });
  it('un doble clic no lanza dos mutaciones', () => {
    componente.marcar(componente.pasos[0], true);
    componente.marcar(componente.pasos[0], true);
    expect(api.actualizarPaso).toHaveBeenCalledTimes(1);
  });
  it('un fallo conserva el paso actual y consulta sin reutilizar caché', () => {
    componente.marcar(componente.pasos[0], true);
    respuesta.error(new Error('Conflicto de versión'));
    expect(componente.pasoActual).toBe(1);
    expect(componente.cumplidos).toBe(0);
    expect(componente.error).toContain('Conflicto');
    expect(api.obtenerPasos).toHaveBeenCalledWith(20, true);
  });
  it('guardar la nota no envía un booleano de cumplimiento antiguo', () => {
    componente.borradores[1] = 'Revisar conexión';
    componente.guardarObservacion(componente.pasos[0]);
    const request = api.actualizarPaso.calls.mostRecent().args[2];
    expect(request.cumplido).toBeUndefined();
    expect(request.versionEsperada).toBe(7);
    expect(componente.observacionListaId).toBeNull();
    respuesta.next({ ...paso(1), observacion: 'Revisar conexión', version: 8 });
    expect(componente.observacionListaId).toBe(1);
  });
  it('conserva texto escrito durante una petición y guarda la versión más reciente', fakeAsync(() => {
    componente.borradores[1] = 'Primera nota';
    componente.guardarObservacion(componente.pasos[0]);
    componente.borradores[1] = 'Segunda nota';
    componente.alEscribirObservacion(componente.pasos[0], 'Segunda nota');
    respuesta.next({ ...paso(1), observacion: 'Primera nota', version: 8 });
    expect(componente.borradores[1]).toBe('Segunda nota');
    expect(componente.observacionListaId).toBeNull();
    tick(800);
    expect(api.actualizarPaso.calls.mostRecent().args[2]).toEqual({ observacion: 'Segunda nota', versionEsperada: 8 });
  }));
  it('agrupa la escritura y no requiere pulsar Guardar', fakeAsync(() => {
    componente.borradores[1] = 'Uno'; componente.alEscribirObservacion(componente.pasos[0], 'Uno');
    tick(500);
    componente.borradores[1] = 'Dos'; componente.alEscribirObservacion(componente.pasos[0], 'Dos');
    tick(799); expect(api.actualizarPaso).not.toHaveBeenCalled();
    tick(1); expect(api.actualizarPaso).toHaveBeenCalledTimes(1);
    expect(api.actualizarPaso.calls.mostRecent().args[2].observacion).toBe('Dos');
  }));
  it('no completa mientras hay una nota sin persistir', () => {
    componente.pasos = [{ ...paso(1), cumplido: true }];
    componente.borradores[1] = 'Pendiente';
    expect(componente.puedeCompletar).toBeFalse();
  });
  it('no permite marcar manualmente una validación externa', () => {
    componente.pasos[0].modoEjecucion = 'VALIDACION_EXTERNA';
    componente.marcar(componente.pasos[0], true);
    expect(api.actualizarPaso).not.toHaveBeenCalled();
  });
  it('bloquea un paso posterior hasta aprobar su predecesor externo', () => {
    componente.pasos[0].modoEjecucion = 'VALIDACION_EXTERNA';
    expect(componente.puedeMarcarPaso(componente.pasos[1])).toBeFalse();
    componente.marcar(componente.pasos[1], true); expect(api.actualizarPaso).not.toHaveBeenCalled();
    componente.pasos[0].cumplido = true;
    expect(componente.puedeMarcarPaso(componente.pasos[1])).toBeTrue();
  });
  it('no mueve el foco si el usuario navegó durante el guardado', () => {
    componente.marcar(componente.pasos[0], true);
    componente.irPaso(2);
    respuesta.next({ ...paso(1), cumplido: true, version: 8 });
    expect(componente.pasoActual).toBe(2);
  });
  it('destruir la pantalla cancela los guardados diferidos', fakeAsync(() => {
    componente.borradores[1] = 'No enviar'; componente.alEscribirObservacion(componente.pasos[0], 'No enviar');
    componente.ngOnDestroy(); tick(1000);
    expect(api.actualizarPaso).not.toHaveBeenCalled();
  }));
  it('un error de guardado no muestra Guardado ni reintenta mutaciones a ciegas', fakeAsync(() => {
    componente.borradores[1] = 'Nota'; componente.guardarObservacion(componente.pasos[0]);
    respuesta.error(new Error('Respuesta perdida'));
    tick(5000);
    expect(componente.observacionListaId).toBeNull();
    expect(api.actualizarPaso).toHaveBeenCalledTimes(1);
    expect(componente.borradores[1]).toBe('Nota');
  }));
  it('protege la salida cuando hay texto o una mutación pendiente', () => {
    const limpio = new Event('beforeunload', { cancelable: true });
    componente.antesDeSalir(limpio as BeforeUnloadEvent); expect(limpio.defaultPrevented).toBeFalse();
    componente.borradores[1] = 'Pendiente';
    const sucio = new Event('beforeunload', { cancelable: true });
    componente.antesDeSalir(sucio as BeforeUnloadEvent); expect(sucio.defaultPrevented).toBeTrue();
    componente.marcar(componente.pasos[0], true); expect(componente.operacionEnCurso()).toBeTrue();
  });
  it('no muestra datos privados de caché si la sesión actual no está autorizada', () => {
    componente.ejecucion = null; componente.pasos = [];
    api.detalleEnSesion.and.returnValue(ejecucion); api.pasosEnSesion.and.returnValue([paso(1)]);
    api.obtener.and.returnValue(throwError(() => new Error('Sin permisos')));
    api.obtenerPasos.and.returnValue(throwError(() => new Error('Sin permisos')));
    componente.ngOnInit();
    expect(componente.ejecucion).toBeNull(); expect(componente.pasos).toEqual([]);
    expect(api.detalleEnSesion).not.toHaveBeenCalled(); expect(api.pasosEnSesion).not.toHaveBeenCalled();
    expect(api.obtener).toHaveBeenCalledWith(20, true); expect(api.obtenerPasos).toHaveBeenCalledWith(20, true);
  });
  it('la sincronización actualiza notas limpias de otra sesión sin sobrescribir borradores locales', fakeAsync(() => {
    spyOnProperty(document, 'visibilityState', 'get').and.returnValue('visible');
    componente.ejecucion = null; componente.pasos = []; componente.ngOnInit();
    api.obtenerPasos.and.returnValue(of([{ ...paso(1), observacion: 'Otra sesión', version: 8 }, paso(2)]));
    tick(20_000); expect(componente.borradores[1]).toBe('Otra sesión'); expect(componente.hayBorradoresPendientes).toBeFalse();
    componente.borradores[1] = 'Mi borrador';
    api.obtenerPasos.and.returnValue(of([{ ...paso(1), observacion: 'Nueva nota externa', version: 9 }, paso(2)]));
    tick(20_000); expect(componente.borradores[1]).toBe('Mi borrador'); expect(componente.hayBorradoresPendientes).toBeTrue();
    componente.ngOnDestroy();
  }));
});
