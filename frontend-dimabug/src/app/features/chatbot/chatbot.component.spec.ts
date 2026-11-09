import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { ChatbotComponent } from './chatbot.component';
import { ChatbotService } from '../../core/services/chatbot.service';
import { ChatbotResponse } from '../../core/models/chatbot.model';

import {AutoservicioService} from '../../core/services/autoservicio.service';

describe('Chatbot recuperado', () => {
  let api: jasmine.SpyObj<ChatbotService>;
  beforeEach(() => {
    api = jasmine.createSpyObj('ChatbotService', ['consultar']);
    TestBed.configureTestingModule({ imports: [ChatbotComponent], providers: [{ provide: ChatbotService, useValue: api },{provide:AutoservicioService,useValue:{materiales:()=>of([{id:2,nombre:'Video público',tipo:'VIDEO',url:'https://example.org/video',publicado:true}]),enlace:(url:string)=>url}}] });
  });
  it('consulta materiales dentro de la tarjeta y permite seguir conversando',fakeAsync(()=>{
    api.consultar.and.returnValue(of({mensaje:'Resultado',soluciones:[{id:27,conocimiento:'Debian',titulo:'Debian',solucion:'Instrucciones'}]}));
    const f=TestBed.createComponent(ChatbotComponent);f.componentInstance.form.setValue({consulta:'Debian'});f.componentInstance.enviar();f.detectChanges();tick();
    const boton=f.nativeElement.querySelector('app-materiales-publicos button');expect(boton.textContent).toContain('Ver material de apoyo');boton.click();f.detectChanges();expect(f.nativeElement.textContent).toContain('Video público');
    f.componentInstance.form.setValue({consulta:'otra consulta'});f.componentInstance.enviar();f.detectChanges();tick();expect(api.consultar).toHaveBeenCalledTimes(2);expect(f.componentInstance.mensajes.length).toBe(5);
  }));
  it('no envía consultas vacías ni mayores de 300 caracteres', () => {
    const c = TestBed.createComponent(ChatbotComponent).componentInstance;
    for (const consulta of [' ', 'x'.repeat(301)]) { c.form.setValue({ consulta }); c.enviar(); }
    expect(api.consultar).not.toHaveBeenCalled();
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
});
