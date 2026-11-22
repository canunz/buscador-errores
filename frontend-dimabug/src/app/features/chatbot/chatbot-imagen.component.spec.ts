import { TestBed } from '@angular/core/testing';
import { ChatbotImagenComponent } from './chatbot-imagen.component';
import { OcrService } from '../../core/services/ocr.service';

describe('Adjunto OCR del chatbot',()=>{
  let ocr:jasmine.SpyObj<OcrService>;
  beforeEach(()=>{
    ocr=jasmine.createSpyObj('ocr',['reconocer','cancelar','liberar']);ocr.reconocer.and.resolveTo({texto:'Error 0x0000011b',confianza:90});
    TestBed.configureTestingModule({imports:[ChatbotImagenComponent]}).overrideComponent(ChatbotImagenComponent,{set:{providers:[{provide:OcrService,useValue:ocr}]}});
  });
  function seleccionar(f:ReturnType<typeof TestBed.createComponent<ChatbotImagenComponent>>) {
    return f.componentInstance.seleccionar({target:{files:[new File(['x'],'error.png',{type:'image/png'})],value:'foto'}} as unknown as Event);
  }
  it('muestra el texto editable y busca solo tras confirmar la corrección',async()=>{
    const f=TestBed.createComponent(ChatbotImagenComponent);const buscar=spyOn(f.componentInstance.buscar,'emit');await seleccionar(f);f.detectChanges();
    expect(f.nativeElement.querySelector('textarea').value).toContain('0x0000011b');expect(buscar).not.toHaveBeenCalled();
    f.componentInstance.texto.setValue('Error corregido');f.componentInstance.confirmar();expect(buscar).toHaveBeenCalledOnceWith('Error corregido');
  });
  it('informa ausencia de texto y fallo del motor sin ofrecer búsqueda vacía',async()=>{
    ocr.reconocer.and.resolveTo({texto:'',confianza:0});const f=TestBed.createComponent(ChatbotImagenComponent);await seleccionar(f);f.detectChanges();
    expect(f.nativeElement.textContent).toContain('No se detectó texto');
    ocr.reconocer.and.rejectWith(new Error('Fallo controlado'));await seleccionar(f);f.detectChanges();expect(f.nativeElement.querySelector('[role=alert]').textContent).toContain('Fallo controlado');
  });
  it('no recorta resultados largos ni confirma mientras hay una búsqueda',async()=>{
    ocr.reconocer.and.resolveTo({texto:'x'.repeat(301),confianza:40});const f=TestBed.createComponent(ChatbotImagenComponent);const buscar=spyOn(f.componentInstance.buscar,'emit');await seleccionar(f);f.detectChanges();
    expect(f.componentInstance.original.length).toBe(301);expect(f.componentInstance.candidatos).toEqual([]);
    f.componentInstance.texto.setValue('x'.repeat(301));f.detectChanges();f.componentInstance.confirmar();expect(buscar).not.toHaveBeenCalled();
    expect(f.nativeElement.textContent).toContain('Selecciona solo');f.componentInstance.texto.setValue('Corregido');f.componentRef.setInput('buscando',true);f.componentInstance.confirmar();expect(buscar).not.toHaveBeenCalled();
  });
  it('cancelar impide que un resultado tardío reaparezca',async()=>{
    let resolver!:(r:{texto:string;confianza:number})=>void;ocr.reconocer.and.returnValue(new Promise(r=>resolver=r));
    const f=TestBed.createComponent(ChatbotImagenComponent);const trabajo=seleccionar(f);f.componentInstance.limpiar();resolver({texto:'Tardío',confianza:90});await trabajo;
    expect(f.componentInstance.estado).toBe('inicial');expect(f.componentInstance.texto.value).toBe('');expect(ocr.liberar).toHaveBeenCalled();
  });
  it('busca un único candidato confirmado y permite cambiarlo sin alterar el original',async()=>{
    const original='TOTAL $33.050\nError al escribir el archivo\n/dim/voucher-v\nS| enta-original_49.txt\nHTTP 503';
    ocr.reconocer.and.resolveTo({texto:original,confianza:80});
    const f=TestBed.createComponent(ChatbotImagenComponent);const buscar=spyOn(f.componentInstance.buscar,'emit');await seleccionar(f);f.detectChanges();
    expect(buscar).not.toHaveBeenCalled();
    const mensaje=f.componentInstance.candidatos.find(c=>c.texto==='Error al escribir el archivo')!;
    f.componentInstance.elegir(mensaje);f.componentInstance.confirmar();
    expect(buscar).toHaveBeenCalledOnceWith('Error al escribir el archivo');expect(f.componentInstance.original).toBe(original);
    f.componentInstance.limpiar();expect(f.componentInstance.original).toBe('');expect(f.componentInstance.candidatos).toEqual([]);
  });
  it('permite escribir una consulta cuando no detecta errores',async()=>{
    ocr.reconocer.and.resolveTo({texto:'Total $30',confianza:80});const f=TestBed.createComponent(ChatbotImagenComponent);
    const buscar=spyOn(f.componentInstance.buscar,'emit');await seleccionar(f);f.detectChanges();
    expect(f.nativeElement.textContent).toContain('No se identificó un mensaje de error');expect(f.componentInstance.texto.value).toBe('');
    f.componentInstance.texto.setValue('Impresora sin conexión');f.componentInstance.confirmar();expect(buscar).toHaveBeenCalledOnceWith('Impresora sin conexión');
  });

  it('abre un modal, conserva el foco y cancela sin buscar',()=>{
    const f=TestBed.createComponent(ChatbotImagenComponent);f.detectChanges();
    const buscar=spyOn(f.componentInstance.buscar,'emit');
    const abrir:HTMLButtonElement=f.nativeElement.querySelector('button');abrir.focus();abrir.click();
    const modal:HTMLDialogElement=f.nativeElement.querySelector('dialog');
    expect(modal.open).toBeTrue();expect(modal.contains(document.activeElement)).toBeTrue();
    f.componentInstance.texto.setValue('Error manual');
    modal.dispatchEvent(new Event('cancel',{cancelable:true}));f.detectChanges();
    expect(modal.open).toBeFalse();expect(buscar).not.toHaveBeenCalled();
    expect(f.componentInstance.texto.value).toBe('');expect(document.activeElement).toBe(abrir);
  });
  it('permite escribir sin texto OCR e impide doble confirmación',async()=>{
    ocr.reconocer.and.resolveTo({texto:'',confianza:0});
    const f=TestBed.createComponent(ChatbotImagenComponent);f.detectChanges();f.componentInstance.abrir();
    await seleccionar(f);f.detectChanges();
    const campo:HTMLTextAreaElement=f.nativeElement.querySelector('textarea');
    campo.value='Error manual';campo.dispatchEvent(new Event('input'));f.detectChanges();
    const buscar=spyOn(f.componentInstance.buscar,'emit');
    f.componentInstance.confirmar();f.componentInstance.confirmar();
    expect(buscar).toHaveBeenCalledOnceWith('Error manual');f.componentInstance.cerrar();
  });
  for(const ancho of [360,1280])it('modal abierto sin desbordamiento horizontal a '+ancho,async()=>{
    const f=TestBed.createComponent(ChatbotImagenComponent);f.detectChanges();
    const frame=document.createElement('iframe');frame.style.width=ancho+'px';frame.style.height='700px';
    const loaded=new Promise<void>(r=>frame.onload=()=>r());
    frame.srcdoc='<!doctype html><html><head>'+Array.from(document.querySelectorAll('style')).map(s=>s.outerHTML).join('')+'</head><body>'+f.nativeElement.outerHTML+'</body></html>';
    document.body.appendChild(frame);try{await loaded;
      const modal=frame.contentDocument!.querySelector('dialog')!;modal.showModal();
      expect(modal.getBoundingClientRect().width).toBeLessThanOrEqual(ancho);
      expect(modal.scrollWidth).toBeLessThanOrEqual(modal.clientWidth);
    }finally{frame.remove();}
  });
});
