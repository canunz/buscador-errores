import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { AdminGmailComponent } from './admin-gmail.component';
import { GmailAdminService, EstadoGmail } from '../../core/services/gmail-admin.service';
import { adminGuard } from '../../core/guards/auth.guard';
import { routes } from '../../app.routes';
describe('Gmail administrativo sin secretos en la interfaz',()=>{
  const estado:EstadoGmail={configurado:true,conectado:true,envioRealHabilitado:false,estado:'ACTIVA',expira:null,remitente:'qa@dimarsa.cl',revocacionRemotaPendiente:false};
  let api:jasmine.SpyObj<GmailAdminService>;
  beforeEach(()=>{api=jasmine.createSpyObj('gmail',['estado','autorizar','revocar','pruebaEstado','enviarPrueba']);api.estado.and.returnValue(of(estado));api.pruebaEstado.and.returnValue(of({remitente:estado.remitente,destinatario:estado.remitente,conectado:true,transporte:'SIMULADO',habilitado:false,ultima:null}));TestBed.configureTestingModule({imports:[AdminGmailComponent],providers:[provideRouter([]),{provide:GmailAdminService,useValue:api}]});});
  it('muestra la conexión separada del envío real y no ofrece activarlo',()=>{
    const f=TestBed.createComponent(AdminGmailComponent);f.detectChanges();expect(f.nativeElement.textContent).toContain('Cuenta autorizada');expect(f.nativeElement.textContent).toContain('envíos reales mediante Gmail API están deshabilitados');expect(f.nativeElement.querySelector('input[type=password]')).toBeNull();f.destroy();
  });
  it('la ruta nueva conserva adminGuard',()=>{const hijos=routes.find(r=>r.children)?.children;expect(hijos?.find(r=>r.path==='admin/gmail')?.canActivate).toContain(adminGuard);});
  it('revocar requiere confirmación y no duplica solicitudes pendientes',()=>{
    const c=TestBed.runInInjectionContext(()=>new AdminGmailComponent());const respuesta=new Subject<EstadoGmail>();api.revocar.and.returnValue(respuesta);c.revocar();expect(api.revocar).not.toHaveBeenCalled();c.pedirRevocacion=true;c.revocar();c.revocar();expect(api.revocar).toHaveBeenCalledTimes(1);respuesta.next({...estado,conectado:false,estado:'REVOCADA'});respuesta.complete();expect(c.ocupado).toBeFalse();expect(c.pedirRevocacion).toBeFalse();
  });
  it('un error de revocación no mantiene una confirmación falsa',()=>{
    const c=TestBed.runInInjectionContext(()=>new AdminGmailComponent());c.estado=estado;c.pedirRevocacion=true;api.revocar.and.returnValue(throwError(()=>new Error('Revocación pendiente')));c.revocar();expect(c.estado).toBeNull();expect(c.error).toContain('pendiente');expect(c.ocupado).toBeFalse();
  });
  it('rechaza una dirección de autorización ajena a Google',()=>{
    const c=TestBed.runInInjectionContext(()=>new AdminGmailComponent());c.estado={...estado,conectado:false};api.autorizar.and.returnValue(of({authorizationUrl:'https://otro.example.invalid/callback'}));c.conectar();expect(c.error).toContain('no es válida');expect(c.ocupado).toBeFalse();
  });
  it('reutiliza botones azul, peligro y secundario con indicadores independientes',()=>{
    const f=TestBed.createComponent(AdminGmailComponent);f.detectChanges();const doc=f.nativeElement;
    expect(doc.querySelector('[data-action=autorizar]').classList.contains('tk-btn-primario')).toBeTrue();
    expect(doc.querySelector('[data-action=revocar]').classList.contains('tk-btn-peligro')).toBeTrue();
    expect(doc.querySelector('[data-action=actualizar]').classList.contains('tk-btn-sec')).toBeTrue();
    expect(doc.querySelector('#gmail-cuenta-titulo').parentElement.textContent).toContain('Conectada');
    expect(doc.querySelector('#gmail-modo-titulo').parentElement.textContent).toContain('Deshabilitado');f.destroy();
  });
  it('abrir o cancelar la confirmación no llama a revocar',()=>{
    const f=TestBed.createComponent(AdminGmailComponent);f.detectChanges();f.nativeElement.querySelector('[data-action=revocar]').click();f.detectChanges();
    const dialog=f.nativeElement.querySelector('[role=alertdialog]');expect(dialog.getAttribute('aria-labelledby')).toBe('gmail-revocar-titulo');expect(dialog.getAttribute('aria-describedby')).toBe('gmail-revocar-descripcion');expect(api.revocar).not.toHaveBeenCalled();
    f.componentInstance.escape();f.detectChanges();expect(f.nativeElement.querySelector('[role=alertdialog]')).toBeNull();expect(api.revocar).not.toHaveBeenCalled();f.destroy();
  });
  it('muestra carga, bloquea acciones y no permite cerrar mientras revoca',()=>{
    const f=TestBed.createComponent(AdminGmailComponent);f.detectChanges();const respuesta=new Subject<EstadoGmail>();api.revocar.and.returnValue(respuesta);f.componentInstance.pedirRevocacion=true;f.detectChanges();
    f.nativeElement.querySelector('[data-action=confirmar-revocacion]').click();f.detectChanges();
    const boton=f.nativeElement.querySelector('[data-action=confirmar-revocacion]');expect(boton.disabled).toBeTrue();expect(boton.textContent).toContain('Revocando');expect(boton.getAttribute('aria-busy')).toBe('true');f.componentInstance.escape();expect(f.componentInstance.pedirRevocacion).toBeTrue();respuesta.next({...estado,conectado:false});respuesta.complete();f.detectChanges();expect(f.componentInstance.ocupado).toBeFalse();f.destroy();
  });
  for(const ancho of [360,1280]){
    it(`adapta tarjetas y botones a ${ancho}px sin desbordar`,async()=>{
      const f=TestBed.createComponent(AdminGmailComponent);f.detectChanges();const frame=document.createElement('iframe');frame.style.width=`${ancho}px`;frame.style.height='900px';const cargado=new Promise<void>(resolve=>frame.onload=()=>resolve());
      const estilos=Array.from(document.querySelectorAll('style')).map(s=>s.outerHTML).join('');frame.srcdoc=`<!doctype html><html><head>${estilos}<style>*{box-sizing:border-box}body{margin:0;padding:16px}</style></head><body>${f.nativeElement.outerHTML}</body></html>`;document.body.appendChild(frame);
      try{await cargado;const doc=frame.contentDocument!;const win=frame.contentWindow!;expect(win.getComputedStyle(doc.querySelector('.gmail-resumen')!).gridTemplateColumns.split(' ').length).toBe(ancho<640?1:2);expect(doc.documentElement.scrollWidth).toBeLessThanOrEqual(ancho);}finally{frame.remove();f.destroy();}
    });
  }
  it('abrir o actualizar la pantalla nunca envía una prueba y SIMULADO la bloquea',()=>{
    const f=TestBed.createComponent(AdminGmailComponent);f.detectChanges();expect(f.nativeElement.querySelector('[data-action=prueba]').disabled).toBeTrue();f.componentInstance.abrirPrueba();expect(f.componentInstance.confirmarPrueba).toBeFalse();expect(api.enviarPrueba).not.toHaveBeenCalled();f.destroy();
  });
  it('pide confirmación con el destino único antes de enviar',()=>{
    api.pruebaEstado.and.returnValue(of({remitente:estado.remitente,destinatario:estado.remitente,conectado:true,transporte:'GMAIL_API',habilitado:true,ultima:null}));
    const f=TestBed.createComponent(AdminGmailComponent);f.detectChanges();f.nativeElement.querySelector('[data-action=prueba]').click();f.detectChanges();const dialog=f.nativeElement.querySelector('[aria-labelledby=gmail-prueba-confirmar-titulo]');expect(dialog.textContent).toContain(estado.remitente);expect(dialog.textContent).toContain('sin CC');expect(api.enviarPrueba).not.toHaveBeenCalled();f.componentInstance.escape();f.detectChanges();expect(f.componentInstance.confirmarPrueba).toBeFalse();f.destroy();
  });
  it('envía una sola solicitud confirmada y bloquea otra tras aceptación',()=>{
    const c=TestBed.runInInjectionContext(()=>new AdminGmailComponent());c.prueba={remitente:estado.remitente,destinatario:estado.remitente,conectado:true,transporte:'GMAIL_API',habilitado:true,ultima:null};const respuesta=new Subject<any>();api.enviarPrueba.and.returnValue(respuesta);
    c.abrirPrueba();c.enviarPrueba();c.enviarPrueba();expect(api.enviarPrueba).toHaveBeenCalledTimes(1);expect(api.enviarPrueba.calls.mostRecent().args[0].destinatario).toBe(estado.remitente);expect(api.enviarPrueba.calls.mostRecent().args[0].confirmado).toBeTrue();expect(c.operacion).toBe('prueba');
    respuesta.next({id:'qa',estado:'ENVIADO',mensajeId:'GMAIL-qa'});respuesta.complete();expect(c.puedeProbar).toBeFalse();expect(c.ocupado).toBeFalse();expect(c.confirmarPrueba).toBeFalse();
  });
  it('una respuesta incierta o perdida no permite reintento automático',()=>{
    const c=TestBed.runInInjectionContext(()=>new AdminGmailComponent());c.prueba={remitente:estado.remitente,destinatario:estado.remitente,conectado:true,transporte:'GMAIL_API',habilitado:true,ultima:null};api.enviarPrueba.and.returnValue(throwError(()=>new Error('Timeout')));c.abrirPrueba();c.enviarPrueba();expect(c.errorPrueba).toContain('Actualiza');expect(c.puedeProbar).toBeFalse();c.enviarPrueba();expect(api.enviarPrueba).toHaveBeenCalledTimes(1);
  });
  it('un fallo definitivo requiere nueva confirmación y referencia al intento anterior',()=>{
    const c=TestBed.runInInjectionContext(()=>new AdminGmailComponent());c.prueba={remitente:estado.remitente,destinatario:estado.remitente,conectado:true,transporte:'GMAIL_API',habilitado:true,ultima:{id:'anterior-qa',estado:'FALLIDO'} as any};api.enviarPrueba.and.returnValue(of({id:'nuevo-qa',estado:'ENVIADO'} as any));c.enviarPrueba();expect(api.enviarPrueba).not.toHaveBeenCalled();c.abrirPrueba();c.enviarPrueba();expect(api.enviarPrueba.calls.mostRecent().args[0].anterior).toBe('anterior-qa');
  });
});
