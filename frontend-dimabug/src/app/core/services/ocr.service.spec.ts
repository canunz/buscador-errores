import { TestBed, fakeAsync, tick, flushMicrotasks } from '@angular/core/testing';
import { OcrService, OcrCancelado, CREAR_WORKER_OCR, PREPARAR_IMAGEN_OCR } from './ocr.service';
import { LIMITES_OCR } from '../models/ocr.model';

describe('OCR local: ciclo de vida y errores', () => {
  let worker: {onmessage: Worker['onmessage']; onerror: Worker['onerror']; onmessageerror: Worker['onmessageerror']; postMessage: jasmine.Spy; terminate: jasmine.Spy};
  let crear: jasmine.Spy; let preparar: jasmine.Spy; let service: OcrService;
  const archivo = new File(['solo prueba'], 'error.png', {type:'image/png'});
  beforeEach(() => {
    worker = {onmessage:null,onerror:null,onmessageerror:null,postMessage:jasmine.createSpy(),terminate:jasmine.createSpy()};
    crear = jasmine.createSpy().and.returnValue(worker);
    preparar = jasmine.createSpy().and.resolveTo(new Blob(['imagen validada']));
    TestBed.configureTestingModule({providers:[OcrService,{provide:CREAR_WORKER_OCR,useValue:crear},{provide:PREPARAR_IMAGEN_OCR,useValue:preparar}]});
    service = TestBed.inject(OcrService);
  });
  afterEach(() => service.ngOnDestroy());
  function responder(data: unknown) { worker.onmessage?.call(worker as unknown as Worker, new MessageEvent('message',{data})); }
  it('procesa en worker, usa recursos del mismo origen y reutiliza el motor',fakeAsync(() => {
    let texto = ''; const progreso = jasmine.createSpy();
    void service.reconocer(archivo,progreso).then(r=>texto=r.texto);flushMicrotasks();
    expect(worker.postMessage.calls.mostRecent().args[0].recursos).toBe(new URL('ocr/v7/',document.baseURI).href);
    responder({tipo:'progreso',progreso:50});expect(progreso).toHaveBeenCalledWith(50);
    responder({tipo:'resultado',texto:' Error 0x0000011b\u0000 ',confianza:90});flushMicrotasks();
    expect(texto).toBe('Error 0x0000011b');
    void service.reconocer(archivo,()=>{});flushMicrotasks();
    expect(crear).toHaveBeenCalledTimes(1);
    responder({tipo:'resultado',texto:'',confianza:0});flushMicrotasks();
  }));
  it('rechaza archivos inválidos sin iniciar el motor',fakeAsync(() => {
    preparar.and.rejectWith(new Error('Formato no permitido'));let error='';
    void service.reconocer(archivo,()=>{}).catch(e=>error=e.message);flushMicrotasks();
    expect(error).toBe('Formato no permitido');expect(crear).not.toHaveBeenCalled();
  }));
  it('cancela un procesamiento y termina el worker',fakeAsync(() => {
    let error:unknown;void service.reconocer(archivo,()=>{}).catch(e=>error=e);flushMicrotasks();
    service.cancelar();flushMicrotasks();expect(error instanceof OcrCancelado).toBeTrue();expect(worker.terminate).toHaveBeenCalledTimes(1);
  }));
  it('cancelar durante preparación impide iniciar un worker tardío',fakeAsync(() => {
    let resolver!:(b:Blob)=>void;preparar.and.returnValue(new Promise<Blob>(r=>resolver=r));
    void service.reconocer(archivo,()=>{}).catch(()=>{});service.cancelar();resolver(new Blob());flushMicrotasks();expect(crear).not.toHaveBeenCalled();
  }));
  it('el plazo máximo termina el trabajo y permite recuperarse',fakeAsync(() => {
    let error='';void service.reconocer(archivo,()=>{}).catch(e=>error=e.message);flushMicrotasks();tick(LIMITES_OCR.plazoMs);flushMicrotasks();
    expect(error).toContain('tardó demasiado');expect(worker.terminate).toHaveBeenCalled();
    void service.reconocer(archivo,()=>{});flushMicrotasks();responder({tipo:'resultado',texto:'Recuperado',confianza:80});flushMicrotasks();expect(crear).toHaveBeenCalledTimes(2);
  }));
  it('el fallo del motor no filtra sus mensajes internos',fakeAsync(() => {
    let error='';void service.reconocer(archivo,()=>{}).catch(e=>error=e.message);flushMicrotasks();responder({tipo:'error',secreto:'contenido privado'});flushMicrotasks();
    expect(error).toContain('No fue posible');expect(error).not.toContain('privado');expect(worker.terminate).toHaveBeenCalled();
  }));
});
