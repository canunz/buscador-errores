import { ApplicationRef } from '@angular/core';
import { ComponentFixture, fakeAsync, flushMicrotasks, TestBed, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { ValidacionExternaComponent } from './validacion-externa.component';
import { AutomatizacionEjecucionService } from '../../core/services/automatizacion-ejecucion.service';
import { AuthService } from '../../core/services/auth.service';

describe('Modal de comunicación integrado en el paso',()=>{
  let fixture:ComponentFixture<ValidacionExternaComponent>;
  beforeEach(async()=>{
    await TestBed.configureTestingModule({imports:[ValidacionExternaComponent],providers:[
      {provide:AuthService,useValue:{isAdmin:()=>false}},
      {provide:AutomatizacionEjecucionService,useValue:{estado:()=>of({solicitudes:[],actividad:[],disponible:true,simulado:true}),sucursales:()=>of([{id:1,codigo:'001',nombre:'Calbuco',ciudad:'Calbuco',responsableNombre:'Jefe de tienda'}]),destinatario:()=>of({id:1,nombre:'Calbuco',responsableNombre:'Responsable QA',responsableCargo:'Jefe de tienda',responsableEmail:'qa@dimarsa.cl',activa:true,destinatarios:[{correo:'qa@dimarsa.cl',tipo:'PRINCIPAL'}]})}},
    ]}).compileComponents();
    fixture=TestBed.createComponent(ValidacionExternaComponent);
    fixture.componentRef.setInput('ejecucionId',15);fixture.componentRef.setInput('enCurso',true);fixture.componentRef.setInput('paso',{ejecucionPasoId:1,pasoId:4,orden:1,instruccion:'Solicitar fotografías',modoEjecucion:'SOLICITAR_EVIDENCIA',esCritico:true,estado:'PENDIENTE'});fixture.detectChanges();
  });
  afterEach(()=>fixture.destroy());
  it('abre evidencias con foco accesible y destinatarios sin sucursal',fakeAsync(()=>{
    const boton=fixture.nativeElement.querySelector('button') as HTMLButtonElement;boton.focus();boton.click();fixture.detectChanges();TestBed.inject(ApplicationRef).tick();flushMicrotasks();tick(0);
    const dialog=fixture.nativeElement.querySelector('[role=dialog]') as HTMLElement;expect(dialog).not.toBeNull();expect(dialog.getAttribute('aria-labelledby')).toBe('correo-titulo');expect(dialog.contains(document.activeElement)).toBeTrue();
    expect(dialog.querySelector('[name=sucursalCorreo]')).toBeNull();expect(dialog.querySelector('.contacto')).toBeNull();
    expect(dialog.textContent).toContain('Para y CC');expect(dialog.textContent).toContain('Evidencias solicitadas');
    expect((dialog.querySelector('input[name=asuntoCorreo]') as HTMLInputElement).readOnly).toBeFalse();
    fixture.componentInstance.escape();fixture.detectChanges();expect(fixture.nativeElement.querySelector('[role=dialog]')).toBeNull();
  }));
  it('no muestra el botón Enviar correo en pasos sin una acción de correo',()=>{fixture.componentRef.setInput('paso',{...fixture.componentInstance.paso,modoEjecucion:'MANUAL_GUIADO'});fixture.detectChanges();expect(fixture.nativeElement.textContent).not.toContain('Enviar correo');});
  it('Enviar correo muestra destinatarios, asunto y mensaje sin sucursal ni colaborador',()=>{
    fixture.componentRef.setInput('paso',{...fixture.componentInstance.paso,modoEjecucion:'ENVIAR_CORREO',esCritico:false});fixture.detectChanges();
    fixture.componentInstance.abrirCorreo();fixture.detectChanges();const dialog=fixture.nativeElement.querySelector('[role=dialog]');
    expect(dialog.querySelector('[name=sucursalCorreo]')).toBeNull();expect(dialog.querySelector('.contacto')).toBeNull();
    expect(dialog.querySelector('[name=asuntoCorreo]').readOnly).toBeFalse();expect(dialog.querySelector('[name=mensajeCorreo]').readOnly).toBeFalse();
    expect(dialog.textContent).toContain('Para y CC');
  });
});
