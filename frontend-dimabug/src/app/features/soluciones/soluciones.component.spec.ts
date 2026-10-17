import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError, Subject } from 'rxjs';
import { SolucionesComponent } from './soluciones.component';
import { ConocimientoService, SolucionCatalogoItem } from '../../core/services/conocimiento.service';
import { AuthService } from '../../core/services/auth.service';
import { Solucion } from '../../core/models/conocimiento.model';

describe('QA-009 administración de soluciones', () => {
  let api: jasmine.SpyObj<ConocimientoService>;
  let admin: boolean;
  const item: SolucionCatalogoItem = { id: 8, titulo: 'Título', descripcion: 'Instrucciones completas', nombre: 'Título', pasos: 'Instrucciones completas', anexos: '', asignaciones: [], conocimientoId: 4, conocimientoTitulo: 'Caso', orden: 1, tipo: 'PASOS', activo: true, autoservicio: false };
  const solucion: Solucion = { id: 8, titulo: 'Título', descripcion: 'Instrucciones completas', tipo: 'PASOS', orden: 1, activo: true, autoservicio: false };
  beforeEach(() => {
    admin = true;
    api = jasmine.createSpyObj('api', ['listarSolucionesCatalogo', 'listar', 'cambiarEstadoSolucion', 'cambiarAutoservicioSolucion', 'crearSolucion', 'modificarSolucion']);
    api.listarSolucionesCatalogo.and.returnValue(of([item])); api.listar.and.returnValue(of([]));
    TestBed.configureTestingModule({ providers: [provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}) } } },
      { provide: ConocimientoService, useValue: api }, { provide: AuthService, useValue: { isAdmin: () => admin } }] });
  });
  it('título obligatorio, sin espacios vacíos y máximo 150', () => {
    const c = TestBed.createComponent(SolucionesComponent).componentInstance;
    c.form.patchValue({ conocimientoId: 4, descripcion: 'D' });
    for (const titulo of ['', '  ', 'x'.repeat(151)]) { c.form.controls.titulo.setValue(titulo); expect(c.form.invalid).toBeTrue(); }
    c.form.controls.titulo.setValue('x'.repeat(150)); expect(c.form.valid).toBeTrue();
  });
  it('solo administrador ve y ejecuta controles administrativos', () => {
    admin = false; const f = TestBed.createComponent(SolucionesComponent); f.detectChanges();
    expect(f.nativeElement.querySelector('.estado-solucion')).toBeNull();
    expect(f.nativeElement.querySelector('.autoservicio-control')).toBeNull();
    f.componentInstance.pedirEstado(item); f.componentInstance.cambiarAutoservicio(item);
    expect(f.componentInstance.pendienteEstado).toBeNull(); expect(api.cambiarAutoservicioSolucion).not.toHaveBeenCalled();
    expect(api.listarSolucionesCatalogo).toHaveBeenCalledWith(false, false);
  });
  it('administrador ve estado y autoservicio', () => {
    const f = TestBed.createComponent(SolucionesComponent); f.detectChanges();
    expect(f.nativeElement.querySelector('.estado-solucion').textContent).toContain('Desactivar');
    expect(f.nativeElement.querySelector('.autoservicio-control')).not.toBeNull();
    expect(f.nativeElement.textContent).toContain('Activa');
  });
  it('desactiva tras respuesta y vuelve a cargar catálogo y detalle', () => {
    const c = TestBed.createComponent(SolucionesComponent).componentInstance;
    c.items = [item]; c.detalle = item; api.cambiarEstadoSolucion.and.returnValue(of({ ...solucion, activo: false }));
    api.listarSolucionesCatalogo.and.returnValue(of([{ ...item, activo: false }]));
    c.pedirEstado(item); c.confirmarEstado();
    expect(api.cambiarEstadoSolucion).toHaveBeenCalledWith(4, 8, false);
    expect(api.listarSolucionesCatalogo).toHaveBeenCalledWith(true, true);
    expect(c.items[0].activo).toBeFalse(); expect(c.detalle?.activo).toBeFalse(); expect(c.pendienteEstado).toBeNull();
  });
  it('RN-08 conserva fila y muestra error sin ocultamiento', () => {
    const c = TestBed.createComponent(SolucionesComponent).componentInstance; c.items = [item];
    api.cambiarEstadoSolucion.and.returnValue(throwError(() => new Error('RN-08: última solución activa')));
    c.pedirEstado(item); c.confirmarEstado();
    expect(c.items).toEqual([item]); expect(c.error).toContain('RN-08'); expect(c.pendienteEstado).toBe(item);
    expect(api.listarSolucionesCatalogo).not.toHaveBeenCalled();
  });
  it('reactivación advierte autoservicio conservado y envía activo true', () => {
    const inactiva = { ...item, activo: false, autoservicio: true };
    api.listarSolucionesCatalogo.and.returnValue(of([inactiva]));
    api.cambiarEstadoSolucion.and.returnValue(of({ ...solucion, autoservicio: true }));
    const f = TestBed.createComponent(SolucionesComponent); f.detectChanges();
    f.componentInstance.pedirEstado(inactiva); f.detectChanges();
    expect(f.nativeElement.querySelector('[role="alertdialog"]').textContent).toContain('conserva el autoservicio habilitado');
    f.componentInstance.confirmarEstado(); expect(api.cambiarEstadoSolucion).toHaveBeenCalledWith(4, 8, true);
  });
  it('autoservicio se actualiza por API y refresca', () => {
    const c = TestBed.createComponent(SolucionesComponent).componentInstance;
    api.cambiarAutoservicioSolucion.and.returnValue(of({ ...solucion, autoservicio: true }));
    c.cambiarAutoservicio(item); expect(api.cambiarAutoservicioSolucion).toHaveBeenCalledWith(4, 8, true);
    expect(api.listarSolucionesCatalogo).toHaveBeenCalledWith(true, true);
  });
  it('solución legacy exige completar título sin perder instrucciones', () => {
    const c = TestBed.createComponent(SolucionesComponent).componentInstance;
    c.openEdit({ ...item, titulo: null }); expect(c.form.controls.titulo.value).toBe('');
    expect(c.form.controls.descripcion.value).toBe(item.descripcion); c.save(); expect(api.modificarSolucion).not.toHaveBeenCalled();
  });
  it('no edita una solución inactiva', () => {
    const c = TestBed.createComponent(SolucionesComponent).componentInstance;
    c.openEdit({ ...item, activo: false }); expect(c.modalOpen).toBeFalse();
  });
  it('no actualiza antes de recibir respuesta HTTP', () => {
    const c = TestBed.createComponent(SolucionesComponent).componentInstance; c.items = [item];
    const respuesta = new Subject<Solucion>(); api.cambiarEstadoSolucion.and.returnValue(respuesta);
    c.pedirEstado(item); c.confirmarEstado(); expect(c.items[0].activo).toBeTrue(); expect(c.operando).toBeTrue();
    respuesta.error(new Error('Sin conexión')); expect(c.error).toBe('Sin conexión'); expect(c.items[0].activo).toBeTrue();
  });
});
