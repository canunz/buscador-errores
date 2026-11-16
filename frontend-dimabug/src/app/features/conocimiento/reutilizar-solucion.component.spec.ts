import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { ConocimientoDetalleComponent } from './conocimiento-detalle.component';
import { ConocimientoService, SolucionCatalogoItem } from '../../core/services/conocimiento.service';
import { AuthService } from '../../core/services/auth.service';
import { OrganizacionService } from '../../core/services/organizacion.service';
import { FavoritosService } from '../../core/services/favoritos.service';
import { Conocimiento, Solucion } from '../../core/models/conocimiento.model';

describe('Reutilizar solución en Resolver', () => {
  let api: jasmine.SpyObj<ConocimientoService>; let admin: boolean;
  let c: ConocimientoDetalleComponent; let fixture: ComponentFixture<ConocimientoDetalleComponent>;
  const original: SolucionCatalogoItem = { id:8,titulo:'Reiniciar servicio',descripcion:'Paso uno',tipo:'PASOS',orden:2,activo:true,autoservicio:true,
    nombre:'Reiniciar servicio',pasos:'Paso uno',anexos:'Origen',asignaciones:[],conocimientoId:1,conocimientoTitulo:'Origen publicado' };
  const copia: Solucion={id:99,titulo:'Reiniciar servicio',descripcion:'Paso uno',tipo:'PASOS',orden:3,activo:true,autoservicio:false};
  beforeEach(() => {
    admin=true; api=jasmine.createSpyObj('api',['listarSolucionesCatalogo','copiarSolucion','listarSoluciones','recordarSeccion','listarAsignaciones']);
    api.listarSolucionesCatalogo.and.returnValue(of([original])); api.copiarSolucion.and.returnValue(of(copia));
    api.listarSoluciones.and.returnValue(of([copia])); api.listarAsignaciones.and.returnValue(of([]));
    TestBed.configureTestingModule({ imports:[ConocimientoDetalleComponent],providers:[provideRouter([]),
      {provide:ActivatedRoute,useValue:{paramMap:of()}},{provide:ConocimientoService,useValue:api},
      {provide:AuthService,useValue:{isAdmin:()=>admin,isStaff:()=>true}},
      {provide:OrganizacionService,useValue:{}},{provide:FavoritosService,useValue:{tiene:()=>false}}] });
    fixture=TestBed.createComponent(ConocimientoDetalleComponent);c=fixture.componentInstance;
    c.item={id:2,titulo:'Destino',estado:'PUBLICADO',creadoPor:{nombre:'Admin'},fechaCreacion:'2026-01-01'} as unknown as Conocimiento;
    c.paso=4;
  });
  it('solo administrador ve y abre Reutilizar solución',()=>{
    fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('Reutilizar solución');admin=false;fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Reutilizar solución');c.abrirReutilizarSolucion();expect(api.listarSolucionesCatalogo).not.toHaveBeenCalled();
  });
  it('carga catálogo administrativo de publicados y excluye destino por ID',()=>{
    api.listarSolucionesCatalogo.and.returnValue(of([original,{...original,id:9,conocimientoId:2}]));c.abrirReutilizarSolucion();
    expect(api.listarSolucionesCatalogo).toHaveBeenCalledWith(true,true,true);expect(c.solucionesReutilizables.length).toBe(1);
  });
  it('busca y presenta conocimiento tipo estado y autoservicio',()=>{
    c.abrirReutilizarSolucion();c.reutilizarForm.controls.busqueda.setValue('reiniciar');expect(c.solucionesReutilizablesFiltradas.length).toBe(1);
    c.seleccionarSolucionReutilizable(original);fixture.detectChanges();const t=fixture.nativeElement.textContent;
    expect(t).toContain('Origen publicado');expect(t).toContain('PASOS');expect(t).toContain('Activa');expect(t).toContain('Autoservicio de origen: Sí');expect(t).toContain('Paso uno');
    c.reutilizarForm.controls.busqueda.setValue('sin coincidencias');expect(c.solucionesReutilizablesFiltradas).toEqual([]);
  });
  it('no copia sin confirmación y no hereda autoservicio',()=>{
    c.abrirReutilizarSolucion();c.seleccionarSolucionReutilizable(original);c.confirmarCopiaSolucion();expect(api.copiarSolucion).not.toHaveBeenCalled();
    expect(c.reutilizarForm.controls.autoservicio.value).toBeFalse();c.reutilizarForm.controls.confirmar.setValue(true);c.confirmarCopiaSolucion();
    expect(api.copiarSolucion).toHaveBeenCalledWith(2,1,8,false);expect(api.listarSoluciones).toHaveBeenCalledWith(2,true);expect(c.soluciones).toEqual([copia]);
  });
  it('advierte exposición en destino PUBLICADO y envía confirmación explícita',()=>{
    c.abrirReutilizarSolucion();c.seleccionarSolucionReutilizable(original);c.reutilizarForm.patchValue({confirmar:true,autoservicio:true});fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('usuarios finales de inmediato');c.confirmarCopiaSolucion();expect(api.copiarSolucion).toHaveBeenCalledWith(2,1,8,true);
  });
  it('seleccionar otra solución reinicia ambas confirmaciones',()=>{c.abrirReutilizarSolucion();c.reutilizarForm.patchValue({confirmar:true,autoservicio:true});c.seleccionarSolucionReutilizable(original);expect(c.reutilizarForm.getRawValue()).toEqual({busqueda:'',confirmar:false,autoservicio:false});});
  it('un POST en vuelo no se duplica por doble clic',()=>{
    const pendiente=new Subject<Solucion>();api.copiarSolucion.and.returnValue(pendiente);c.abrirReutilizarSolucion();c.seleccionarSolucionReutilizable(original);c.reutilizarForm.controls.confirmar.setValue(true);
    c.confirmarCopiaSolucion();c.confirmarCopiaSolucion();expect(api.copiarSolucion.calls.count()).toBe(1);expect(c.copiandoSolucion).toBeTrue();pendiente.next(copia);
  });
  it('duplicado o permiso conserva selección y muestra mensaje sin reintento',()=>{
    api.copiarSolucion.and.returnValue(throwError(()=>new Error('409: ya existe una solución')));c.abrirReutilizarSolucion();c.seleccionarSolucionReutilizable(original);c.reutilizarForm.controls.confirmar.setValue(true);c.confirmarCopiaSolucion();
    expect(c.copiaError).toContain('ya existe');expect(c.reutilizarAbierto).toBeTrue();expect(api.copiarSolucion.calls.count()).toBe(1);expect(api.listarSoluciones).not.toHaveBeenCalled();
  });
  it('si falla recarga posterior solo reintenta GET y no POST',()=>{
    api.listarSoluciones.and.returnValue(throwError(()=>new Error('Lectura falló')));c.abrirReutilizarSolucion();c.seleccionarSolucionReutilizable(original);c.reutilizarForm.controls.confirmar.setValue(true);c.confirmarCopiaSolucion();
    expect(c.copiaError).toContain('ya se copió');expect(c.copiaCompletada).toBeTrue();c.confirmarCopiaSolucion();api.listarSoluciones.and.returnValue(of([copia]));c.recargarTrasCopia();expect(api.copiarSolucion.calls.count()).toBe(1);expect(c.reutilizarAbierto).toBeFalse();
  });
  it('informa copia inactiva sin mostrarla en Resolver',()=>{
    api.copiarSolucion.and.returnValue(of({...copia,activo:false}));api.listarSoluciones.and.returnValue(of([]));c.abrirReutilizarSolucion();c.seleccionarSolucionReutilizable({...original,activo:false});c.reutilizarForm.controls.confirmar.setValue(true);c.confirmarCopiaSolucion();
    expect(c.soluciones).toEqual([]);expect(c.sectionSuccess).toContain('inactiva copiada');
  });
  it('rol no administrador no puede copiar mediante método',()=>{
    c.abrirReutilizarSolucion();c.seleccionarSolucionReutilizable(original);c.reutilizarForm.controls.confirmar.setValue(true);admin=false;c.confirmarCopiaSolucion();expect(api.copiarSolucion).not.toHaveBeenCalled();
  });
});
