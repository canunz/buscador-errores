import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { EjecucionChecklistComponent } from './ejecucion-checklist.component';
import { EjecucionService } from '../../core/services/ejecucion.service';
import { ProcedimientoService } from '../../core/services/procedimiento.service';
import { Ejecucion, EjecucionPaso } from '../../core/models/ejecucion.model';

describe('Ejecución: carga, vacío y error de conectividad', () => {
  let fixture: ComponentFixture<EjecucionChecklistComponent>;
  let api: jasmine.SpyObj<EjecucionService>;
  const ejecucion: Ejecucion = { id: 15, procedimientoId: 1, estado: 'EN_CURSO', fechaInicio: null, fechaFin: null, observaciones: null, usuario: { id: 1, nombre: 'QA', email: 'qa@example.invalid' } };
  const pasos: EjecucionPaso[] = Array.from({ length: 6 }, (_, i) => ({ ejecucionPasoId: i + 1, pasoId: i + 1, orden: i + 1, instruccion: `Instrucción ${i + 1}`, esCritico: false, cumplido: false, observacion: null, fecha: null, modoEjecucion: 'MANUAL_GUIADO', version: 0 }));
  beforeEach(async () => {
    api = jasmine.createSpyObj('ejecuciones', ['obtener', 'obtenerPasos', 'marcaPasos', 'obtenerProcedimiento']);
    api.obtener.and.returnValue(of(ejecucion)); api.obtenerPasos.and.returnValue(of(pasos)); api.marcaPasos.and.returnValue(0);
    api.obtenerProcedimiento.and.returnValue(of({ id: 1, nombre: 'Preparar POS', descripcion: null, estado: 'PUBLICADO' }));
    await TestBed.configureTestingModule({ imports: [EjecucionChecklistComponent], providers: [provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '15' } } } },
      { provide: EjecucionService, useValue: api },
      { provide: ProcedimientoService, useValue: { listar: () => of([]), listaEnSesion: () => [], listarMateriales: () => of([]) } },
    ] }).compileComponents();
    fixture = TestBed.createComponent(EjecucionChecklistComponent);
  });
  afterEach(() => fixture.destroy());
  const texto = () => fixture.nativeElement.textContent as string;

  it('muestra cargando sin afirmar que no existen pasos', () => {
    api.obtenerPasos.and.returnValue(new Subject<EjecucionPaso[]>()); fixture.detectChanges();
    expect(texto()).toContain('Cargando pasos del procedimiento'); expect(texto()).not.toContain('Este procedimiento no tiene pasos');
  });
  it('solo muestra vacío tras una respuesta correcta sin pasos', () => {
    api.obtenerPasos.and.returnValue(of([])); fixture.detectChanges();
    expect(texto()).toContain('Este procedimiento no tiene pasos'); expect(texto()).not.toContain('No fue posible cargar');
  });
  it('un fallo de conexión muestra error y permite recuperar los seis pasos sin crear ejecuciones', () => {
    api.obtenerPasos.and.returnValue(throwError(() => new Error('ERR_CONNECTION_REFUSED'))); fixture.detectChanges();
    expect(texto()).toContain('No fue posible cargar los pasos del procedimiento. Compruebe la conexión e intente nuevamente');
    expect(texto()).not.toContain('Este procedimiento no tiene pasos');
    api.obtenerPasos.and.returnValue(of(pasos));
    const boton = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(b => b.textContent?.trim() === 'Reintentar')!;
    boton.click(); fixture.detectChanges();
    expect(fixture.componentInstance.pasos.length).toBe(6); expect(texto()).toContain('1 de 6');
    expect(texto()).not.toContain('No fue posible cargar los pasos'); expect(api.obtenerPasos).toHaveBeenCalledWith(15, true);
  });
  it('un doble reintento no duplica consultas pendientes', () => {
    api.obtenerPasos.and.returnValue(throwError(() => new Error('Sin conexión'))); fixture.detectChanges();
    api.obtenerPasos.and.returnValue(new Subject<EjecucionPaso[]>());
    fixture.componentInstance.reintentarCarga(); fixture.componentInstance.reintentarCarga();
    expect(api.obtenerPasos).toHaveBeenCalledTimes(2);
  });
  it('permite reconsultar la ejecución si fallan ambos GET', () => {
    api.obtener.and.returnValue(throwError(() => new Error('Sin conexión')));
    api.obtenerPasos.and.returnValue(throwError(() => new Error('Sin conexión'))); fixture.detectChanges();
    expect(texto()).toContain('No pudimos abrir esta ejecución'); expect(texto()).toContain('Reintentar');
    api.obtener.and.returnValue(of(ejecucion)); api.obtenerPasos.and.returnValue(of(pasos));
    fixture.componentInstance.reintentarCarga(); fixture.detectChanges();
    expect(fixture.componentInstance.ejecucion?.id).toBe(15); expect(fixture.componentInstance.pasos.length).toBe(6);
  });
});
