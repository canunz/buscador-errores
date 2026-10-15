import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { ConocimientoDetalleComponent } from './conocimiento-detalle.component';
import { ConocimientoService } from '../../core/services/conocimiento.service';
import { AuthService } from '../../core/services/auth.service';
import { OrganizacionService } from '../../core/services/organizacion.service';
import { FavoritosService } from '../../core/services/favoritos.service';
import { Conocimiento, Solucion } from '../../core/models/conocimiento.model';

describe('QA-009 soluciones en detalle de conocimiento', () => {
  let api: jasmine.SpyObj<ConocimientoService>;
  const solucion: Solucion = { id: 8, titulo: 'Título', descripcion: 'Instrucciones', tipo: 'PASOS', orden: 1, activo: true, autoservicio: true };
  beforeEach(() => {
    api = jasmine.createSpyObj('api', ['crearSolucion', 'modificarSolucion', 'recordarSeccion']);
    TestBed.configureTestingModule({ providers: [provideRouter([]),
      { provide: ActivatedRoute, useValue: { paramMap: of() } },
      { provide: ConocimientoService, useValue: api },
      { provide: AuthService, useValue: { isAdmin: () => false, isStaff: () => true } },
      { provide: OrganizacionService, useValue: {} }, { provide: FavoritosService, useValue: {} }] });
  });
  function componente() {
    const c = TestBed.createComponent(ConocimientoDetalleComponent).componentInstance;
    c.item = { id: 4 } as Conocimiento; return c;
  }
  it('título obligatorio en editor de detalle', () => {
    const c = componente(); c.solucionForm.patchValue({ descripcion: 'D' }); c.guardarSolucion();
    expect(api.crearSolucion).not.toHaveBeenCalled(); expect(c.solucionForm.controls.titulo.touched).toBeTrue();
  });
  it('legacy necesita título conservando las instrucciones', () => {
    const c = componente(); c.editarSolucion({ ...solucion, titulo: null });
    expect(c.solucionForm.controls.titulo.value).toBe(''); expect(c.solucionForm.controls.descripcion.value).toBe('Instrucciones');
    c.guardarSolucion(); expect(api.modificarSolucion).not.toHaveBeenCalled();
  });
  it('actualiza detalle y caché solo después del éxito', () => {
    const c = componente(); const respuesta = new Subject<Solucion>(); api.crearSolucion.and.returnValue(respuesta);
    c.solucionForm.setValue({ titulo: 'Título', descripcion: 'Instrucciones', tipo: 'PASOS', orden: 1 });
    c.formAbierto.soluciones = true; c.guardarSolucion(); expect(c.soluciones).toEqual([]);
    expect(api.recordarSeccion).not.toHaveBeenCalled(); respuesta.next(solucion);
    expect(c.soluciones).toEqual([solucion]); expect(api.recordarSeccion).toHaveBeenCalledWith('soluciones:4', [solucion]);
    expect(c.formAbierto.soluciones).toBeFalse();
  });
  it('un error HTTP mantiene formulario y datos para corregir', () => {
    const c = componente(); c.soluciones = [solucion]; c.editarSolucion(solucion);
    api.modificarSolucion.and.returnValue(throwError(() => new Error('RN-16: solución inactiva')));
    c.guardarSolucion(); expect(c.soluciones).toEqual([solucion]); expect(c.formAbierto.soluciones).toBeTrue();
    expect(c.solucionForm.controls.titulo.value).toBe('Título'); expect(c.sectionError).toContain('RN-16');
  });
});
