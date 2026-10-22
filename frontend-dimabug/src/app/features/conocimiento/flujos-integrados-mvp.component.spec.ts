import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { ConocimientoDetalleComponent } from './conocimiento-detalle.component';
import { ConocimientoService } from '../../core/services/conocimiento.service';
import { AuthService } from '../../core/services/auth.service';
import { OrganizacionService } from '../../core/services/organizacion.service';
import { FavoritosService } from '../../core/services/favoritos.service';
import { Conocimiento } from '../../core/models/conocimiento.model';

describe('Candidato integrado: Verificar, Resolver y Apoyo', () => {
  let api: any, c: ConocimientoDetalleComponent, fixture: ComponentFixture<ConocimientoDetalleComponent>, admin: boolean;
  const prueba = { id: 7, descripcion: 'Ping', resultadoEsperado: 'Responde', activo: true };
  const asociada = { ...prueba, activa: true, orden: 1 };
  const solucion = { id: 8, titulo: 'Solución', descripcion: 'Paso seguro', tipo: 'PASOS' as const, orden: 1,
    activo: true, autoservicio: false, conocimientoId: 1, conocimientoTitulo: 'Origen' };
  const material = { id: 3, nombre: 'Manual', tipo: 'PDF' as const,
    url: '/api/conocimientos/1/materiales/3/archivo', conocimientoId: 1, conocimientoTitulo: 'Origen' };
  beforeEach(() => {
    admin = true;
    api = jasmine.createSpyObj('api', ['asociarPrueba', 'listarPruebas', 'recordarSeccion',
      'listarSolucionesCatalogo', 'copiarSolucion', 'listarSoluciones', 'listarAsignaciones',
      'listarMaterialesCatalogo', 'reutilizarMaterial', 'listarMateriales']);
    api.asociarPrueba.and.returnValue(of(asociada)); api.listarPruebas.and.returnValue(of([asociada]));
    api.listarSolucionesCatalogo.and.returnValue(of([solucion]));
    api.copiarSolucion.and.returnValue(of({ ...solucion, id: 99 }));
    api.listarSoluciones.and.returnValue(of([{ ...solucion, id: 99 }]));
    api.listarAsignaciones.and.returnValue(of([]));
    api.listarMaterialesCatalogo.and.returnValue(of([material]));
    api.reutilizarMaterial.and.returnValue(of({ ...material, id: 100 }));
    api.listarMateriales.and.returnValue(of([{ ...material, id: 100 }]));
    TestBed.configureTestingModule({ imports: [ConocimientoDetalleComponent], providers: [
      provideRouter([]), { provide: ActivatedRoute, useValue: { paramMap: of() } },
      { provide: ConocimientoService, useValue: api },
      { provide: AuthService, useValue: { isAdmin: () => admin, isStaff: () => true } },
      { provide: OrganizacionService, useValue: { listarPruebas: () => of([prueba]) } },
      { provide: FavoritosService, useValue: { tiene: () => false } },
    ] });
    fixture = TestBed.createComponent(ConocimientoDetalleComponent); c = fixture.componentInstance;
    c.item = { id: 4, titulo: 'Destino', estado: 'BORRADOR', fechaCreacion: '2026-01-01' } as Conocimiento;
  });
  it('conserva asociaciones y listas al recorrer los tres pasos en el mismo componente', () => {
    c.paso = 3; c.abrirBuscarPrueba(); c.pruebaForm.controls.pruebaId.setValue(7); c.guardarPrueba();
    expect(c.pruebas[0].id).toBe(7);
    c.paso = 4; c.abrirReutilizarSolucion(); c.seleccionarSolucionReutilizable(solucion as any);
    c.reutilizarForm.controls.confirmar.setValue(true); c.confirmarCopiaSolucion();
    expect(c.soluciones[0].id).toBe(99); expect(c.pruebas[0].id).toBe(7);
    c.paso = 5; c.abrirBuscarMaterial(); c.seleccionarMaterial(material); c.reutilizarMaterialSeleccionado();
    fixture.detectChanges();
    expect(c.materiales[0].id).toBe(100); expect(c.soluciones[0].id).toBe(99); expect(c.pruebas[0].id).toBe(7);
    expect(api.copiarSolucion).toHaveBeenCalledOnceWith(4, 1, 8, false);
    expect(api.reutilizarMaterial).toHaveBeenCalledOnceWith(4, 1, 3);
    expect(api.asociarPrueba).toHaveBeenCalledOnceWith(4, { pruebaId: 7, orden: 1 });
  });
  it('un fallo de copia no impide usar Apoyo ni pierde su confirmación para revisión', () => {
    api.copiarSolucion.and.returnValue(throwError(() => new Error('409 duplicado')));
    c.abrirReutilizarSolucion(); c.seleccionarSolucionReutilizable(solucion as any);
    c.reutilizarForm.controls.confirmar.setValue(true); c.confirmarCopiaSolucion();
    expect(c.copiaError).toContain('409'); c.cerrarReutilizarSolucion();
    c.abrirBuscarMaterial(); c.seleccionarMaterial(material); c.reutilizarMaterialSeleccionado();
    expect(c.materiales[0].id).toBe(100); expect(c.solucionSeleccionada?.id).toBe(8);
    expect(api.copiarSolucion.calls.count()).toBe(1);
  });
  it('TECNICO conserva asociación y materiales pero no copia soluciones administrativas', () => {
    admin = false; c.abrirReutilizarSolucion();
    expect(api.listarSolucionesCatalogo).not.toHaveBeenCalled();
    c.abrirBuscarPrueba(); c.pruebaForm.controls.pruebaId.setValue(7); c.guardarPrueba();
    c.abrirBuscarMaterial(); c.seleccionarMaterial(material); c.reutilizarMaterialSeleccionado();
    expect(api.asociarPrueba).toHaveBeenCalled(); expect(api.reutilizarMaterial).toHaveBeenCalled();
    expect(api.copiarSolucion).not.toHaveBeenCalled();
  });
});
