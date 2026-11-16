import { fakeAsync, flushMicrotasks, TestBed } from '@angular/core/testing';
import { cambiosProcedimientoGuard } from './cambios-procedimiento.guard';
import { Ejecucion } from '../../core/models/ejecucion.model';
import { ActivatedRoute, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { ProcedimientoDetalleComponent } from './procedimiento-detalle.component';
import { ProcedimientoService } from '../../core/services/procedimiento.service';
import { EjecucionService } from '../../core/services/ejecucion.service';
import { AutomatizacionEjecucionService } from '../../core/services/automatizacion-ejecucion.service';
import { AuthService } from '../../core/services/auth.service';
describe('Inicio con sucursal y destinatario', () => {
  let c: ProcedimientoDetalleComponent;
  let auto: jasmine.SpyObj<AutomatizacionEjecucionService>;
  let ejecuciones: jasmine.SpyObj<EjecucionService>;
  let router: jasmine.SpyObj<Router>;
  const creada: Ejecucion = { id: 14, procedimientoId: 10, estado: 'EN_CURSO', fechaInicio: '2026-11-07T23:46:29', fechaFin: null, observaciones: null, usuario: { id: 1, nombre: "QA", email: "qa@example.invalid" } };
  beforeEach(() => {
    auto = jasmine.createSpyObj('auto', ['sucursales','destinatario']); ejecuciones = jasmine.createSpyObj('ejecuciones', ['iniciar']);
    router = jasmine.createSpyObj('router', ['navigate']); router.navigate.and.returnValue(Promise.resolve(true));
    auto.sucursales.and.returnValue(of([{ id: 1, codigo: 'QA', nombre: 'Sintética', responsableNombre: 'Jefe QA' }]));
    auto.destinatario.and.returnValue(of({ id: 1, nombre: 'Sintética', responsableNombre: 'Jefe QA', responsableEmail: 'qa-no-enviar@dimarsa.cl', activa: true }));
    TestBed.configureTestingModule({ providers: [{ provide: AutomatizacionEjecucionService, useValue: auto }, { provide: EjecucionService, useValue: ejecuciones },
      { provide: ProcedimientoService, useValue: {} }, { provide: AuthService, useValue: { isAdmin: () => true, isStaff: () => true } },
      { provide: ActivatedRoute, useValue: {} }, { provide: Router, useValue: router }] });
    c = TestBed.runInInjectionContext(() => new ProcedimientoDetalleComponent());
    c.item = { id: 10, nombre: 'Preparar POS', estado: 'PUBLICADO', descripcion: null, creadoPor: null, modificadoPor: null, fechaCreacion: null, fechaModificacion: null };
    c.pasos = [{ id: 1, procedimientoId: 10, orden: 1, instruccion: 'Instalaciones', esCritico: true, modoEjecucion: 'VALIDACION_EXTERNA' }];
  });
  it('inicia un procedimiento con correo sin pedir sucursal ni destinatarios', () => {
    ejecuciones.iniciar.and.returnValue(new Subject()); c.iniciar();
    expect(ejecuciones.iniciar).toHaveBeenCalledWith(10); expect(auto.sucursales).not.toHaveBeenCalled();
  });
  it('el procedimiento manual mantiene el inicio anterior', () => { c.pasos[0].modoEjecucion = 'MANUAL'; ejecuciones.iniciar.and.returnValue(new Subject()); c.iniciar(); expect(ejecuciones.iniciar).toHaveBeenCalledWith(10); expect(auto.sucursales).not.toHaveBeenCalled(); });
  it('libera el guard antes de navegar al ID recibido y termina la carga', fakeAsync(() => {
    c.pasos[0].modoEjecucion = 'MANUAL'; ejecuciones.iniciar.and.returnValue(of(creada));
    router.navigate.and.callFake(() => {
      expect(c.iniciando).toBeFalse();
      expect(cambiosProcedimientoGuard(c, {} as any, {} as any, {} as any)).toBeTrue();
      return Promise.resolve(true);
    });
    c.iniciar(); expect(router.navigate).toHaveBeenCalledWith(['/ejecuciones', 14]);
    flushMicrotasks(); expect(c.iniciando).toBeFalse(); expect(c.navegandoEjecucion).toBeFalse(); expect(c.error).toBe('');
  }));
  it('una navegación cancelada permite abrir la misma ejecución sin otro POST', fakeAsync(() => {
    c.pasos[0].modoEjecucion = 'MANUAL'; ejecuciones.iniciar.and.returnValue(of(creada));
    router.navigate.and.returnValue(Promise.resolve(false)); c.iniciar(); flushMicrotasks();
    expect(c.error).toContain('fue creada'); expect(c.iniciando).toBeFalse(); expect(c.navegandoEjecucion).toBeFalse();
    router.navigate.and.returnValue(Promise.resolve(true)); c.iniciar(); flushMicrotasks();
    expect(ejecuciones.iniciar).toHaveBeenCalledTimes(1); expect(router.navigate).toHaveBeenCalledTimes(2); expect(c.error).toBe('');
  }));
  it('captura el rechazo de navegación y conserva el ID para reintentar', fakeAsync(() => {
    c.pasos[0].modoEjecucion = 'MANUAL'; ejecuciones.iniciar.and.returnValue(of(creada));
    router.navigate.and.callFake(() => Promise.reject(new Error('Fallo de ruta'))); c.iniciar(); flushMicrotasks();
    expect(c.error).toContain('error al abrirla'); expect(c.ejecucionCreadaId).toBe(14); expect(c.navegandoEjecucion).toBeFalse();
    router.navigate.and.returnValue(Promise.resolve(true)); c.iniciar(); flushMicrotasks(); expect(ejecuciones.iniciar).toHaveBeenCalledTimes(1);
  }));
  it('el error HTTP libera la carga y no intenta navegar', () => {
    c.pasos[0].modoEjecucion = 'MANUAL'; ejecuciones.iniciar.and.returnValue(throwError(() => new Error('No se pudo conectar'))); c.iniciar();
    expect(c.iniciando).toBeFalse(); expect(c.error).toBe('No se pudo conectar'); expect(router.navigate).not.toHaveBeenCalled(); expect(c.ejecucionCreadaId).toBeNull();
  });
  it('bloquea doble clic durante HTTP y durante la navegación automatizada', fakeAsync(() => {
    const respuesta = new Subject<Ejecucion>(); ejecuciones.iniciar.and.returnValue(respuesta);
    let resolver!: (valor: boolean) => void; router.navigate.and.returnValue(new Promise<boolean>(r => { resolver = r; }));
    c.iniciar(); c.iniciar(); expect(ejecuciones.iniciar).toHaveBeenCalledTimes(1);
    respuesta.next(creada); respuesta.complete(); c.iniciar(); expect(ejecuciones.iniciar).toHaveBeenCalledTimes(1); expect(router.navigate).toHaveBeenCalledTimes(1);
    resolver(true); flushMicrotasks(); expect(c.navegandoEjecucion).toBeFalse(); expect(c.iniciando).toBeFalse();
  }));
});
