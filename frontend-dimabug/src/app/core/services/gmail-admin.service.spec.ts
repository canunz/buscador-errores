import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { GmailAdminService } from './gmail-admin.service';
describe('Contrato administrativo de Gmail',()=>{
  let api:GmailAdminService;let http:HttpTestingController;
  beforeEach(()=>{TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]});api=TestBed.inject(GmailAdminService);http=TestBed.inject(HttpTestingController);});
  afterEach(()=>http.verify());
  it('inicia autorización sin enviar secretos desde Angular',()=>{api.autorizar().subscribe();const r=http.expectOne(q=>q.url.endsWith('/admin/gmail/autorizacion'));expect(r.request.method).toBe('POST');expect(r.request.body).toEqual({});r.flush({authorizationUrl:'https://accounts.google.com/o/oauth2/v2/auth'});});
  it('consulta estado sin obtener tokens',()=>{api.estado().subscribe();const r=http.expectOne(q=>q.url.endsWith('/admin/gmail'));expect(r.request.method).toBe('GET');r.flush({configurado:false,conectado:false});});
  it('revoca mediante el endpoint administrativo existente',()=>{api.revocar().subscribe();const r=http.expectOne(q=>q.url.endsWith('/admin/gmail/revocar'));expect(r.request.method).toBe('POST');expect(r.request.body).toEqual({});r.flush({conectado:false});});
  it('consulta la prueba mediante GET sin ejecutar envíos',()=>{api.pruebaEstado().subscribe();const r=http.expectOne(q=>q.url.endsWith('/admin/gmail/prueba'));expect(r.request.method).toBe('GET');r.flush({conectado:true,transporte:'SIMULADO',habilitado:false,ultima:null});});
  it('envía únicamente destino y clave de la prueba confirmada, sin credenciales ni CC',()=>{const datos={clave:'clave-qa',anterior:null,confirmado:true as const,destinatario:'qa@dimarsa.cl'};api.enviarPrueba(datos).subscribe();const r=http.expectOne(q=>q.url.endsWith('/admin/gmail/prueba'));expect(r.request.method).toBe('POST');expect(r.request.body).toEqual(datos);r.flush({id:'clave-qa',estado:'ENVIADO'});});
});
