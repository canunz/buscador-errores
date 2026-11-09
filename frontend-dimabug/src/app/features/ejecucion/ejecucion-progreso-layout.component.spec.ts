import { of } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { EjecucionChecklistComponent } from './ejecucion-checklist.component';
import { EjecucionService } from '../../core/services/ejecucion.service';
import { ProcedimientoService } from '../../core/services/procedimiento.service';

describe('Ejecución: ubicación y anclaje del progreso',()=>{
  let fixture:ComponentFixture<EjecucionChecklistComponent>;
  beforeEach(()=>{
    TestBed.configureTestingModule({imports:[EjecucionChecklistComponent],providers:[provideRouter([]),
      {provide:ActivatedRoute,useValue:{snapshot:{paramMap:{get:()=>null}}}},{provide:EjecucionService,useValue:{}},{provide:ProcedimientoService,useValue:{listarMateriales:()=>of([{id:5,nombre:"Configuración Login",tipo:"VIDEO",url:"https://drive.google.com/file/d/recurso-sintetico/view"}])}}]});
    fixture=TestBed.createComponent(EjecucionChecklistComponent);
    fixture.detectChanges();
    const c=fixture.componentInstance;c.error='';
    c.ejecucion={id:21,procedimientoId:10,estado:'EN_CURSO',fechaInicio:null,fechaFin:null,observaciones:null,usuario:{id:1,nombre:'QA',email:'qa@example.invalid'}};
    c.procedimiento={id:10,nombre:'Preparar POS',descripcion:null,estado:'PUBLICADO'};
    c.pasosConsultados=true;
    c.pasos=[{ejecucionPasoId:1,pasoId:1,orden:1,instruccion:'Verificar instalaciones',esCritico:true,cumplido:false,observacion:null,fecha:null,version:1,modoEjecucion:'MANUAL_GUIADO',estado:'PENDIENTE'}];
    fixture.detectChanges();
  });
  afterEach(()=>fixture.destroy());
  it('presenta una sola barra inmediatamente después de la cabecera y antes de los pasos',()=>{
    const doc=fixture.nativeElement;
    expect(doc.querySelectorAll('.ej-cierre').length).toBe(1);
    expect(doc.querySelector('.ej-franja').nextElementSibling.classList.contains('ej-cierre')).toBeTrue();
    expect(doc.querySelector('.ej-cierre').compareDocumentPosition(doc.querySelector('.ej-cuerpo')) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(doc.querySelector('.ej-cierre-texto').textContent).toContain('Falta 1 paso');
  });
  it('conserva las condiciones y acciones de los botones',()=>{
    const c=fixture.componentInstance;const finalizar=fixture.nativeElement.querySelector('.ej-cierre .tk-btn-exito');
    expect(finalizar.disabled).toBeTrue();
    c.pasos=[{...c.pasos[0],cumplido:true,estado:'COMPLETADO'}];fixture.detectChanges();
    expect(finalizar.disabled).toBeFalse();
    const completar=spyOn(c,'completar');finalizar.click();expect(completar).toHaveBeenCalledTimes(1);
    const cancelar=spyOn(c,'pedirCancelar');fixture.nativeElement.querySelector('.ej-cierre .tk-btn-peligro').click();expect(cancelar).toHaveBeenCalledTimes(1);
    c.gestionandoEvidencia=true;fixture.detectChanges();expect(finalizar.disabled).toBeTrue();
  });
  it('no confunde errores o carga de pasos con progreso válido',()=>{
    const c=fixture.componentInstance;c.cargandoPasos=true;fixture.detectChanges();expect(fixture.nativeElement.querySelector('.ej-cierre')).toBeNull();
    c.cargandoPasos=false;c.errorPasos='Error de conexión';fixture.detectChanges();expect(fixture.nativeElement.querySelector('.ej-cierre')).toBeNull();
  });
  it('oculta las acciones cuando la ejecución terminó',()=>{
    fixture.componentInstance.ejecucion!.estado='COMPLETADA';fixture.detectChanges();expect(fixture.nativeElement.querySelector('.ej-cierre')).toBeNull();
  });
  it('mantiene el material de un paso completado y del resumen sin alterar el progreso',()=>{
    const c=fixture.componentInstance;
    c.pasos=[{...c.pasos[0],cumplido:true,estado:'COMPLETADO'}];const antes=JSON.stringify(c.pasos);
    const completar=spyOn(c,'completar');const avanzar=spyOn(c,'completarYAvanzar');fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.ej-panel app-materiales-ejecucion').textContent).toContain('Configuración Login');
    c.ejecucion!.estado='COMPLETADA';fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.ej-resumen app-materiales-ejecucion').textContent).toContain('Configuración Login');
    expect(JSON.stringify(c.pasos)).toBe(antes);expect(completar).not.toHaveBeenCalled();expect(avanzar).not.toHaveBeenCalled();
  });
  for(const ancho of [360,1280]){
    it(`mantiene visible la barra al desplazar a ${ancho}px sin desbordar`,async()=>{
      const frame=document.createElement('iframe');frame.style.width=`${ancho}px`;frame.style.height='700px';
      const cargado=new Promise<void>(resolve=>frame.onload=()=>resolve());
      const estilos=Array.from(document.querySelectorAll('style')).map(s=>s.outerHTML).join('');
      frame.srcdoc=`<!doctype html><html><head>${estilos}<style>*{box-sizing:border-box}body{margin:0;padding:16px}.ej-panel{min-height:2000px}</style></head><body>${fixture.nativeElement.outerHTML}</body></html>`;
      document.body.appendChild(frame);
      try{
        await cargado;const doc=frame.contentDocument!;const win=frame.contentWindow!;
        const barra=doc.querySelector('.ej-cierre') as HTMLElement;
        const cabecera=doc.querySelector('.ej-franja') as HTMLElement;
        // Terminar la animación de entrada de la cabecera antes de medir posiciones.
        doc.querySelectorAll('*').forEach(e=>(e as HTMLElement).style.animation='none');
        expect(win.getComputedStyle(barra).position).toBe('sticky');
        expect(barra.getBoundingClientRect().top).toBeGreaterThanOrEqual(cabecera.getBoundingClientRect().bottom);
        const origen=barra.getBoundingClientRect().top;win.scrollTo(0,origen+300);
        // La lectura geométrica fuerza layout incluso con Chrome de pruebas en segundo plano.
        expect(Math.round(barra.getBoundingClientRect().top)).toBe(ancho<560?8:12);
        expect(cabecera.getBoundingClientRect().bottom).toBeLessThan(barra.getBoundingClientRect().top);
        expect(win.getComputedStyle(doc.querySelector('.ej-pasos')!).position).toBe('static');
        expect(doc.documentElement.scrollWidth).toBeLessThanOrEqual(ancho);
      }finally{frame.remove();}
    });
  }
});
