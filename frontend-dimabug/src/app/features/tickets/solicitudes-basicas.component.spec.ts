import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { TicketService } from '../../core/services/ticket.service';
import { AuthService } from '../../core/services/auth.service';
import { SolicitudesBasicasComponent } from './solicitudes-basicas.component';
describe('Portal de tickets propios',()=>{
 let api:jasmine.SpyObj<TicketService>;
 const ticket:any={id:1,codigo:'TK-2026-0001',titulo:'Problema sintético',descripcion:'No puedo ingresar',estado:'ABIERTO',fechaCreacion:'2026-11-15T10:00:00',fechaActualizacion:'2026-11-15T10:00:00',adjuntos:[]};
 beforeEach(()=>{
  api=jasmine.createSpyObj('TicketService',['listar','crearSolicitud','obtener','limitesSolicitud','archivo','sugerencias','catalogosSolicitud']);
  api.catalogosSolicitud.and.returnValue(of({sucursales:[{id:1,nombre:'Sucursal QA'}],departamentos:[{id:2,nombre:'Área QA'}]}));
  api.listar.and.returnValue(of({contenido:[],pagina:0,tamano:10,totalElementos:0,totalPaginas:0}));
  api.limitesSolicitud.and.returnValue(of({cantidad:5,bytes:5242880}));api.obtener.and.returnValue(of(ticket));
  TestBed.configureTestingModule({imports:[SolicitudesBasicasComponent],providers:[{provide:TicketService,useValue:api},{provide:AuthService,useValue:{logout:()=>{}}}]});
 });
 it('reutiliza estilos, permite fotos y no expone prioridad ni directorios',()=>{
  const f=TestBed.createComponent(SolicitudesBasicasComponent);f.detectChanges();
  expect(f.nativeElement.querySelector('input[type=file]').multiple).toBeTrue();
  expect(f.nativeElement.querySelector('select[name=prioridad]')).toBeNull();expect(f.nativeElement.querySelectorAll('select').length).toBe(2);expect(api.sugerencias).not.toHaveBeenCalled();
  expect(f.nativeElement.textContent).toContain('Todavía no tienes solicitudes registradas');f.destroy();
 });
 it('crea solo con título, descripción y fotografías, evitando doble clic',()=>{
  const pendiente=new Subject<any>();api.crearSolicitud.and.returnValue(pendiente);
  const f=TestBed.createComponent(SolicitudesBasicasComponent),c=f.componentInstance;
  c.solicitante={nombreCompleto:'Persona QA',sucursalId:1,departamentoId:2,contacto:'1234',equipo:'',codigoUsuario:''};
  c.titulo=' Prueba ';c.descripcion=' Sintético ';c.crear();c.crear();
  expect(api.crearSolicitud).toHaveBeenCalledOnceWith('Prueba','Sintético',[],c.solicitante);expect(c.enviando).toBeTrue();
  pendiente.next(ticket);pendiente.complete();expect(c.enviando).toBeFalse();expect(c.titulo).toBe('');expect(c.exito).toContain(ticket.codigo);f.destroy();
 });
 it('no envía vacío o sin límites verificados',()=>{
  const f=TestBed.createComponent(SolicitudesBasicasComponent),c=f.componentInstance;c.crear();expect(api.crearSolicitud).not.toHaveBeenCalled();
  c.titulo='T';c.descripcion='D';c.limitesListos=false;c.crear();expect(api.crearSolicitud).not.toHaveBeenCalled();f.destroy();
 });
 it('valida tipo, tamaño y cantidad y libera las vistas previas',()=>{
  const f=TestBed.createComponent(SolicitudesBasicasComponent),c=f.componentInstance;
  const evento=(files:File[])=>({target:{files,value:'x'}} as unknown as Event);
  c.seleccionar(evento([new File(['x'],'x.pdf',{type:'application/pdf'})]));expect(c.fotos.length).toBe(0);expect(c.error).toContain('JPG');
  c.maxBytes=2;c.seleccionar(evento([new File(['xxx'],'x.png',{type:'image/png'})]));expect(c.fotos.length).toBe(0);
  c.maxBytes=5242880;c.maxFotos=1;c.seleccionar(evento([new File(['x'],'a.png',{type:'image/png'})]));expect(c.fotos.length).toBe(1);
  c.seleccionar(evento([new File(['x'],'b.png',{type:'image/png'})]));expect(c.fotos.length).toBe(1);expect(c.error).toContain('hasta 1');
  c.quitar(0);expect(c.fotos.length).toBe(0);f.destroy();
 });
 it('conserva el formulario ante error y advierte antes de reenviar',()=>{
  api.crearSolicitud.and.returnValue(throwError(()=>new Error('offline')));
  const f=TestBed.createComponent(SolicitudesBasicasComponent),c=f.componentInstance;c.solicitante={nombreCompleto:'Persona QA',sucursalId:1,departamentoId:2,contacto:'1234',equipo:'',codigoUsuario:''};c.titulo='T';c.descripcion='D';c.crear();
  expect(c.enviando).toBeFalse();expect(c.titulo).toBe('T');expect(c.error).toContain('historial');f.destroy();
 });
 it('carga fotografías solo mediante API protegida y muestra errores comprensibles',()=>{
  api.obtener.and.returnValue(of({...ticket,adjuntos:[{id:2,nombre:'foto.png'}]}));api.archivo.and.returnValue(throwError(()=>new Error('denied')));
  const f=TestBed.createComponent(SolicitudesBasicasComponent),c=f.componentInstance;c.abrir(1);
  expect(api.archivo).toHaveBeenCalledOnceWith(1,2);expect(c.errorDetalle).toContain('fotografía');expect(c.imagenes).toEqual([]);f.destroy();
 });
 it('muestra estado, fechas e historial existente sin información interna',()=>{
  api.listar.and.returnValue(of({contenido:[ticket],pagina:0,tamano:10,totalElementos:1,totalPaginas:1}));
  const f=TestBed.createComponent(SolicitudesBasicasComponent);f.detectChanges();expect(f.nativeElement.textContent).toContain('TK-2026-0001');expect(f.nativeElement.textContent).toContain('Actualizado');expect(f.nativeElement.textContent).toContain('Abierto');f.destroy();
 });
 it('exige datos del solicitante, pero permite equipo y código vacíos',()=>{
  const f=TestBed.createComponent(SolicitudesBasicasComponent),c=f.componentInstance;
  c.titulo='T';c.descripcion='D';c.crear();expect(api.crearSolicitud).not.toHaveBeenCalled();
  c.solicitante={nombreCompleto:'Persona QA',sucursalId:1,departamentoId:2,contacto:'+56 9 1234 5678',equipo:'',codigoUsuario:''};
  expect(c.datosValidos()).toBeTrue();
  c.solicitante.equipo='999.1.1.1';expect(c.datosValidos()).toBeFalse();
  c.solicitante.equipo='123 456 789';expect(c.datosValidos()).toBeTrue();
  c.solicitante.sucursalId=999;expect(c.datosValidos()).toBeFalse();f.destroy();
 });
 it('mantiene el envío bloqueado si falla cargar los selectores y permite reintentar',()=>{
  api.catalogosSolicitud.and.returnValue(throwError(()=>new Error('offline')));
  const f=TestBed.createComponent(SolicitudesBasicasComponent),c=f.componentInstance;
  expect(c.catalogosListos).toBeFalse();expect(c.errorCatalogos).toContain('No pudimos');
  c.crear();expect(api.crearSolicitud).not.toHaveBeenCalled();
  api.catalogosSolicitud.and.returnValue(of({sucursales:[{id:1,nombre:'Sucursal QA'}],departamentos:[{id:2,nombre:'Área QA'}]}));
  c.cargarCatalogos();expect(c.catalogosListos).toBeTrue();f.destroy();
 });

});
