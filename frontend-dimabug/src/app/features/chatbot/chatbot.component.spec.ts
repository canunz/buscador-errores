import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { ChatbotComponent } from './chatbot.component';
import { ChatbotService } from '../../core/services/chatbot.service';
import {provideRouter} from '@angular/router';
import {AuthService} from '../../core/services/auth.service';
import { ChatbotResponse } from '../../core/models/chatbot.model';

import {AutoservicioService} from '../../core/services/autoservicio.service';

describe('Chatbot recuperado', () => {
  let api: jasmine.SpyObj<ChatbotService>;
  beforeEach(() => {
    api = jasmine.createSpyObj('ChatbotService', ['consultar']);
    TestBed.configureTestingModule({ imports: [ChatbotComponent], providers: [provideRouter([]),{provide:AuthService,useValue:{isAuthenticated:()=>false,isAdmin:()=>false}},{ provide: ChatbotService, useValue: api },{provide:AutoservicioService,useValue:{materiales:()=>of([{id:2,nombre:'Video público',tipo:'VIDEO',url:'https://example.org/video',publicado:true}]),enlace:(url:string)=>url}}] });
  });
  it('consulta materiales dentro de la tarjeta y permite seguir conversando',fakeAsync(()=>{
    api.consultar.and.returnValue(of({mensaje:'Resultado',soluciones:[{id:27,conocimiento:'Debian',titulo:'Debian',solucion:'Instrucciones'}]}));
    const f=TestBed.createComponent(ChatbotComponent);f.componentInstance.form.setValue({consulta:'Debian'});f.componentInstance.enviar();f.detectChanges();tick();
    const boton=f.nativeElement.querySelector('app-materiales-publicos button');expect(boton.textContent).toContain('Ver material de apoyo');boton.click();f.detectChanges();expect(f.nativeElement.textContent).toContain('Video público');
    f.componentInstance.form.setValue({consulta:'otra consulta'});f.componentInstance.enviar();f.detectChanges();tick();expect(api.consultar).toHaveBeenCalledTimes(2);expect(f.componentInstance.mensajes.length).toBe(5);
  }));
  it('usa texto OCR confirmado y conserva el flujo de materiales y conversación',fakeAsync(()=>{
    api.consultar.and.returnValue(of({mensaje:'Sin resultados',soluciones:[]}));const f=TestBed.createComponent(ChatbotComponent);
    f.componentInstance.buscarDesdeImagen('Error 0x0000011b');f.detectChanges();tick();
    expect(api.consultar).toHaveBeenCalledOnceWith('Error 0x0000011b');expect(f.nativeElement.textContent).toContain('Iniciar sesión para solicitar soporte');
    f.componentInstance.form.setValue({consulta:'Otra consulta'});f.componentInstance.enviar();tick();expect(api.consultar).toHaveBeenCalledTimes(2);
  }));
  it('no envía consultas vacías ni mayores de 300 caracteres', () => {
    const c = TestBed.createComponent(ChatbotComponent).componentInstance;
    for (const consulta of [' ', 'x'.repeat(301)]) { c.form.setValue({ consulta }); c.enviar(); }
    expect(api.consultar).not.toHaveBeenCalled();
  });
  it('CP-OCR-006 presenta soluciones y materiales de una consulta OCR confirmada',fakeAsync(()=>{
    api.consultar.and.returnValue(of({mensaje:'Resultado',soluciones:[{id:27,conocimiento:'Autoservicio',titulo:'Error de impresión',solucion:'Instrucciones publicadas'}]}));
    const f=TestBed.createComponent(ChatbotComponent);f.componentInstance.buscarDesdeImagen('Error 0x0000011b');f.detectChanges();tick();
    expect(api.consultar).toHaveBeenCalledOnceWith('Error 0x0000011b');
    expect(f.nativeElement.querySelector('.solucion').textContent).toContain('Instrucciones publicadas');
    expect(f.nativeElement.querySelector('app-materiales-publicos button').textContent).toContain('Ver material de apoyo');
  }));
  for(const ancho of [360,1280])it('conserva el ancho responsive del chat y del adjunto a '+ancho,async()=>{
    const f=TestBed.createComponent(ChatbotComponent);f.detectChanges();const frame=document.createElement('iframe');
    frame.style.width=ancho+'px';frame.style.height='700px';const loaded=new Promise<void>(r=>frame.onload=()=>r());
    frame.srcdoc='<!doctype html><html><head>'+Array.from(document.querySelectorAll('style')).map(s=>s.outerHTML).join('')+'<style>*{box-sizing:border-box}body{margin:0}</style></head><body>'+f.nativeElement.outerHTML+'</body></html>';
    document.body.appendChild(frame);try{await loaded;expect(frame.contentDocument!.documentElement.scrollWidth).toBeLessThanOrEqual(ancho);}finally{frame.remove();}
  });
  it('muestra respuesta real y trata instrucciones como texto', fakeAsync(() => {
    api.consultar.and.returnValue(of({ mensaje: 'Resultado', soluciones: [{ conocimiento: 'Caso', titulo: 'Paso', solucion: '<img src=x onerror=alert(1)>' }] }));
    const f = TestBed.createComponent(ChatbotComponent);
    f.componentInstance.form.setValue({ consulta: ' impresora ' }); f.componentInstance.enviar(); f.detectChanges(); tick();
    expect(api.consultar).toHaveBeenCalledWith('impresora');
    expect(f.nativeElement.querySelector('.solucion').textContent).toContain('<img src=x onerror=alert(1)>');
    expect(f.nativeElement.querySelector('.solucion img')).toBeNull();
  }));
  it('bloquea solicitudes simultáneas y muestra derivación', fakeAsync(() => {
    const pendiente = new Subject<ChatbotResponse>(); api.consultar.and.returnValue(pendiente);
    const c = TestBed.createComponent(ChatbotComponent).componentInstance;
    c.form.setValue({ consulta: 'papel' }); c.enviar();
    c.form.setValue({ consulta: 'otra' }); c.enviar();
    expect(c.enviando).toBeTrue(); expect(api.consultar).toHaveBeenCalledTimes(1);
    pendiente.next({ mensaje: 'Contacta a Soporte', soluciones: [] }); pendiente.complete(); tick();
    expect(c.enviando).toBeFalse(); expect(c.mensajes.at(-1)?.texto).toBe('Contacta a Soporte');
  }));
  it('muestra error y permite volver a consultar', fakeAsync(() => {
    api.consultar.and.returnValue(throwError(() => new Error('No disponible')));
    const c = TestBed.createComponent(ChatbotComponent).componentInstance;
    c.form.setValue({ consulta: 'papel' }); c.enviar(); tick();
    expect(c.enviando).toBeFalse(); expect(c.mensajes.at(-1)?.texto).toBe('No disponible');
  }));

  it('cierra el modal al aceptar, conserva la consulta fallida y permite reintentar',fakeAsync(()=>{
    api.consultar.and.returnValue(throwError(()=>new Error('No disponible')));
    const f=TestBed.createComponent(ChatbotComponent);f.detectChanges();
    f.nativeElement.querySelector('app-chatbot-imagen button').click();
    expect(f.nativeElement.querySelector('dialog').open).toBeTrue();
    f.componentInstance.buscarDesdeImagen('Error confirmado');f.detectChanges();tick();
    expect(f.nativeElement.querySelector('dialog').open).toBeFalse();
    expect(f.nativeElement.querySelector('.usuario').textContent).toContain('Error confirmado');
    api.consultar.and.returnValue(of({mensaje:'Solución encontrada',soluciones:[]}));
    f.nativeElement.querySelector('.reintentar').click();f.detectChanges();tick();
    expect(api.consultar.calls.allArgs()).toEqual([['Error confirmado'],['Error confirmado']]);
    expect(f.nativeElement.textContent).toContain('Solución encontrada');
  }));
});
