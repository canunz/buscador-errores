import { configuracionInicial } from '../../core/models/configuracion-paso.model';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { ProcedimientoDetalleComponent } from './procedimiento-detalle.component';
import { ProcedimientoService } from '../../core/services/procedimiento.service';
import { EjecucionService } from '../../core/services/ejecucion.service';
import { AuthService } from '../../core/services/auth.service';
import { PasoProcedimiento } from '../../core/models/procedimiento.model';
describe('QA013 Pasos: orden y confirmación', () => {
 let admin=true;
 let api: any; let c: ProcedimientoDetalleComponent; let fixture: any;
 const pasos: PasoProcedimiento[] = [{ id: 5, procedimientoId: 1, orden: 4, instruccion: 'A', esCritico: false }, { id: 6, procedimientoId: 1, orden: 10, instruccion: 'B', esCritico: true }];
 beforeEach(() => { admin=true;api = jasmine.createSpyObj('api', ['crearPaso','actualizarPaso','listarPasos','materialesEnCache','marcaMateriales','listarMateriales','moverPaso','eliminarPaso','historial']); api.crearPaso.and.returnValue(of(pasos[0])); api.actualizarPaso.and.returnValue(of(pasos[0])); api.listarPasos.and.returnValue(of(pasos)); api.materialesEnCache.and.returnValue([]); api.marcaMateriales.and.returnValue(0); api.listarMateriales.and.returnValue(of([]));
   TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => null } } } }, { provide: ProcedimientoService, useValue: api }, { provide: EjecucionService, useValue: {} }, { provide: AuthService, useValue: { isAdmin: () => admin, isStaff: () => true } }] });
   fixture = TestBed.createComponent(ProcedimientoDetalleComponent); c = fixture.componentInstance; c.item = { id: 1, nombre: 'Guía', estado: 'BORRADOR' } as any; c.pasos = pasos; });
 it('presenta posición y orden separado con saltos', () => { fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('Paso 1 de 2 · Orden 4'); c.irPaso(1); fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('Paso 2 de 2 · Orden 10'); expect(fixture.nativeElement.textContent).not.toContain('Paso 10 de 2'); });
 it('rechaza orden fraccionario sin HTTP', () => { c.instruccion = 'X'; c.orden = 1.5; c.guardarPaso(); expect(api.crearPaso).not.toHaveBeenCalled(); expect(c.error).toContain('entero'); });
 it('bloquea edición en PUBLICADO también en método', () => { c.item!.estado = 'PUBLICADO'; c.abrirPaso(); c.instruccion = 'X'; c.guardarPaso(); expect(c.pasoAbierto).toBeFalse(); expect(api.crearPaso).not.toHaveBeenCalled(); expect(c.error).toContain('BORRADOR'); });
 it('solo confirma tras HTTP y recarga Pasos', () => { const respuesta = new Subject<PasoProcedimiento>(); api.crearPaso.and.returnValue(respuesta); c.abrirPaso(); c.instruccion = 'X'; c.guardarPaso(); expect(c.aviso).toBe(''); expect(c.guardandoPaso).toBeTrue(); expect(c.pasoAbierto).toBeTrue(); respuesta.next(pasos[0]); expect(c.aviso).toContain('guardado'); expect(c.pasoAbierto).toBeFalse(); expect(api.listarPasos).toHaveBeenCalledWith(1, true); });
 it('error conserva formulario y no confirma', () => { api.crearPaso.and.returnValue(throwError(() => new Error('Orden ocupado'))); c.abrirPaso(); c.instruccion = 'X'; c.guardarPaso(); expect(c.pasoAbierto).toBeTrue(); expect(c.error).toContain('ocupado'); expect(c.aviso).toBe(''); });
 it('muestra Editar procedimiento solo al administrador y conserva Iniciar para el técnico',()=>{c.item!.estado='PUBLICADO';fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('Editar procedimiento');admin=false;fixture.detectChanges();expect(fixture.nativeElement.textContent).not.toContain('Editar procedimiento');expect(fixture.nativeElement.textContent).toContain('Iniciar procedimiento');c.abrirPaso(pasos[0]);expect(c.pasoAbierto).toBeFalse();});
 it('cada paso del borrador tiene acciones accesibles y límites de movimiento',()=>{fixture.detectChanges();expect(fixture.nativeElement.querySelectorAll('.paso-admin button').length).toBe(8);expect(fixture.nativeElement.querySelector('[aria-label="Mover arriba paso 4"]').disabled).toBeTrue();expect(fixture.nativeElement.querySelector('[aria-label="Mover abajo paso 10"]').disabled).toBeTrue();});
 it('mueve una sola vez mientras espera y conserva seleccionado el mismo paso',()=>{const respuesta=new Subject<PasoProcedimiento[]>();api.moverPaso.and.returnValue(respuesta);c.moverPaso(pasos[1],'ARRIBA');c.moverPaso(pasos[1],'ARRIBA');expect(api.moverPaso).toHaveBeenCalledTimes(1);expect(api.moverPaso).toHaveBeenCalledWith(1,6,'ARRIBA',[5,6]);respuesta.next([{...pasos[1],orden:1},{...pasos[0],orden:2}]);expect(c.pasoActual?.id).toBe(5);expect(c.pasoIndex).toBe(1);expect(c.gestionandoPasos).toBeFalse();});
 it('eliminar exige confirmar y no repite la solicitud durante el guardado',()=>{const respuesta=new Subject<void>();api.eliminarPaso.and.returnValue(respuesta);api.listarPasos.and.returnValue(of([pasos[1]]));c.pedirEliminarPaso(pasos[0]);expect(api.eliminarPaso).not.toHaveBeenCalled();c.eliminarPasoConfirmado();c.eliminarPasoConfirmado();expect(api.eliminarPaso).toHaveBeenCalledTimes(1);respuesta.next();expect(c.pasos.map(p=>p.id)).toEqual([6]);expect(c.pasoAEliminar).toBeNull();});

 it('crear correo envía su configuración mediante el servicio existente',()=>{
   c.abrirPaso();c.instruccion='Avisar a Control Cajas';c.modoEjecucion='ENVIAR_CORREO';
   c.configuracionPaso=configuracionInicial('ENVIAR_CORREO',false,{asunto:'POS preparado',mensaje:'Solicitar folios',destinatarios:[{correo:'qa@dimarsa.cl',tipo:'PRINCIPAL'}]});
   c.guardarPaso();const body=api.crearPaso.calls.mostRecent().args[1];
   expect(body.modoEjecucion).toBe('ENVIAR_CORREO');expect(body.configuracion.asunto).toBe('POS preparado');
   expect(body.configuracion.destinatarios[0].correo).toBe('qa@dimarsa.cl');
 });
 for(const modo of ['INFORMATIVO','AUTOMATICO','VALIDACION_EXTERNA'] as const){
   it(`editar instrucción conserva modalidad y configuración de ${modo}`,()=>{
     const config=configuracionInicial(modo,false);const paso={...pasos[0],modoEjecucion:modo,configuracion:config};
     c.abrirPaso(paso);c.instruccion='Instrucción editada';c.guardarPaso();
     const body=api.actualizarPaso.calls.mostRecent().args[2];
     expect(body.modoEjecucion).toBe(modo);expect(body.configuracion).toEqual(config);
   });
 }
 it('marcar crítico envía aprobación y exige confirmación en lugar de finalizar al mostrar',()=>{
   c.abrirPaso({...pasos[0],modoEjecucion:'INFORMATIVO',configuracion:configuracionInicial('INFORMATIVO',false,{condicionFinalizacion:'AL_ACTIVAR',requiereAprobacion:false})});
   c.esCritico=true;c.actualizarCriticidad();c.guardarPaso();const body=api.actualizarPaso.calls.mostRecent().args[2];
   expect(body.esCritico).toBeTrue();expect(body.configuracion.requiereAprobacion).toBeTrue();
   expect(body.configuracion.condicionFinalizacion).toBe('CONFIRMACION_USUARIO');
 });
});
