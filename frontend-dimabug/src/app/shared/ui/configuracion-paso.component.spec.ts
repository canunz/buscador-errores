import { ComponentFixture, TestBed } from '@angular/core/testing';
import { configuracionInicial } from '../../core/models/configuracion-paso.model';
import { ConfiguracionPasoComponent } from './configuracion-paso.component';

describe('Configuración del paso: secciones y controles condicionales', () => {
  let fixture: ComponentFixture<ConfiguracionPasoComponent>;
  beforeEach(async () => { await TestBed.configureTestingModule({ imports: [ConfiguracionPasoComponent] }).compileComponents(); fixture=TestBed.createComponent(ConfiguracionPasoComponent); });
  afterEach(()=>fixture.destroy());
  it('un paso manual oculta destinatarios y contenido de correo',()=>{fixture.componentRef.setInput('modo','MANUAL_GUIADO');fixture.detectChanges();const t=fixture.nativeElement.textContent;expect(t).toContain('¿Qué debe hacer el técnico?');expect(t).not.toContain('Configuración del correo');expect(fixture.nativeElement.querySelector('details')).toBeNull();});
  it('evidencias usa opciones individuales y conserva el asunto',()=>{fixture.componentRef.setInput('modo','SOLICITAR_EVIDENCIA');fixture.detectChanges();expect(fixture.nativeElement.querySelector('select[multiple]')).toBeNull();expect(fixture.nativeElement.textContent).toContain('Conexión eléctrica');fixture.componentInstance.config.asunto='Solicitud POS';fixture.componentInstance.alternarEvidencia('FOTO_GENERAL');expect(fixture.componentInstance.config.evidenciasRequeridas).toContain('FOTO_GENERAL');expect(fixture.componentInstance.config.asunto).toBe('Solicitud POS');});
  it('correo sin evidencias oculta las categorías y permite configurar el seguimiento',()=>{fixture.componentRef.setInput('modo','ENVIAR_CORREO');fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('Destinatarios');expect(fixture.nativeElement.textContent).not.toContain('Evidencias requeridas');fixture.componentInstance.config.condicionFinalizacion='RESPUESTA_VALIDADA';fixture.componentInstance.ajustarCondicion();fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('Plazo de respuesta');});
  it('presenta exactamente tres acciones sin etiquetas técnicas',()=>{
    fixture.detectChanges();const radios=fixture.nativeElement.querySelectorAll('input[type=radio]');
    expect(radios.length).toBe(3);expect(fixture.nativeElement.textContent).toContain('Seguir instrucciones');
    expect(fixture.nativeElement.querySelector('select')).toBeNull();
  });
  it('cambiar la acción muestra correo y volver recupera las evidencias sin perder el borrador',()=>{
    fixture.componentRef.setInput('modo','SOLICITAR_EVIDENCIA');
    fixture.componentRef.setInput('value',configuracionInicial('SOLICITAR_EVIDENCIA',false,{asunto:'Fotos POS',evidenciasRequeridas:['FOTO_GENERAL'],recordatoriosHoras:[48],condicionFinalizacion:'EVIDENCIAS_RECIBIDAS',requiereAprobacion:false}));
    fixture.detectChanges();const c=fixture.componentInstance;
    c.cambiarModalidad('ENVIAR_CORREO');fixture.detectChanges();
    expect(c.config.condicionFinalizacion).toBe('ENVIO_REGISTRADO');
    expect(fixture.nativeElement.textContent).not.toContain('Evidencias requeridas');
    c.cambiarModalidad('SOLICITAR_EVIDENCIA');fixture.detectChanges();
    expect(c.config.asunto).toBe('Fotos POS');expect(c.config.evidenciasRequeridas).toEqual(['FOTO_GENERAL']);
    expect(c.config.recordatoriosHoras).toEqual([48]);expect(c.config.condicionFinalizacion).toBe('EVIDENCIAS_RECIBIDAS');
  });
  it('abrir un informativo no cambia su modalidad ni finalización',()=>{
    const emit=spyOn(fixture.componentInstance.modoChange,'emit');
    fixture.componentRef.setInput('modo','INFORMATIVO');
    fixture.componentRef.setInput('value',configuracionInicial('INFORMATIVO',false,{condicionFinalizacion:'AL_ACTIVAR'}));fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input[type=radio]').checked).toBeTrue();
    expect(fixture.componentInstance.config.condicionFinalizacion).toBe('AL_ACTIVAR');expect(emit).not.toHaveBeenCalled();
    fixture.componentInstance.cambiarModalidad('ENVIAR_CORREO');fixture.componentInstance.cambiarModalidad('MANUAL_GUIADO');
    expect(emit).toHaveBeenCalledWith('INFORMATIVO');expect(fixture.componentInstance.config.condicionFinalizacion).toBe('AL_ACTIVAR');
  });
  it('conserva la acción automática existente y permite editar sus opciones',()=>{
    const config=configuracionInicial('AUTOMATICO',false,{accionAutomatica:'VALIDAR_CONTEXTO_SUCURSAL',requiereAprobacion:true});
    fixture.componentRef.setInput('modo','AUTOMATICO');fixture.componentRef.setInput('value',config);fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('input[type=radio]:checked').length).toBe(0);
    expect(fixture.componentInstance.config).toEqual(config);expect(fixture.nativeElement.textContent).toContain('conserva su acción automática');
  });
  for(const modo of ['MANUAL','VALIDACION_EXTERNA'] as const){
    it(`preserva el alias histórico ${modo} al editar sin cambiar la acción`,()=>{
      fixture.componentRef.setInput('modo',modo);fixture.detectChanges();
      const emit=spyOn(fixture.componentInstance.modoChange,'emit');
      fixture.componentInstance.config.asunto='Actualizado';fixture.componentInstance.emitir();
      expect(emit).not.toHaveBeenCalled();expect(fixture.componentInstance.modo).toBe(modo);
    });
  }
  it('un paso crítico mantiene aprobación obligatoria',async()=>{
    fixture.componentRef.setInput('modo','SOLICITAR_EVIDENCIA');fixture.componentRef.setInput('critico',true);fixture.detectChanges();
    expect(fixture.componentInstance.config.requiereAprobacion).toBeTrue();
    await fixture.whenStable();fixture.detectChanges();
    const check=fixture.nativeElement.querySelector('details input[type=checkbox]');expect(check.disabled).toBeTrue();
  });
  it('la vista deshabilitada no permite cambiar la acción',()=>{
    fixture.componentRef.setInput('disabled',true);fixture.detectChanges();const emit=spyOn(fixture.componentInstance.modoChange,'emit');
    fixture.componentInstance.cambiarModalidad('ENVIAR_CORREO');expect(emit).not.toHaveBeenCalled();
  });
  for(const ancho of [360,960]){
    it(`distribuye acciones y correo sin desbordar a ${ancho}px`,async()=>{
      fixture.componentRef.setInput('modo','SOLICITAR_EVIDENCIA');fixture.detectChanges();
      const frame=document.createElement('iframe');frame.style.width=`${ancho}px`;frame.style.height='900px';
      const cargado=new Promise<void>(resolve=>frame.onload=()=>resolve());
      const estilos=Array.from(document.querySelectorAll('style')).map(s=>s.outerHTML).join('');
      frame.srcdoc=`<!doctype html><html><head>${estilos}</head><body>${fixture.nativeElement.outerHTML}</body></html>`;
      document.body.appendChild(frame);
      try{
        await cargado;
        const doc=frame.contentDocument!;const acciones=doc.querySelector('.acciones')!;
        const columnas=frame.contentWindow!.getComputedStyle(acciones).gridTemplateColumns.split(' ').length;
        expect(columnas).toBe(ancho<600?1:3);
        expect(doc.documentElement.scrollWidth).toBeLessThanOrEqual(ancho);
      }finally{frame.remove();}
    });
  }
});
