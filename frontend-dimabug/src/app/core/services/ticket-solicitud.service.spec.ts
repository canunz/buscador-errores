import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting,HttpTestingController } from '@angular/common/http/testing';
import { TicketService } from './ticket.service';
import { AuthService } from './auth.service';
describe('Contrato privado de creación de solicitudes',()=>{
 let api:TicketService;let http:HttpTestingController;
 beforeEach(()=>{TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting(),{provide:AuthService,useValue:{usuario:()=>({usuarioId:1,accesoBasico:true})}}]});api=TestBed.inject(TicketService);http=TestBed.inject(HttpTestingController);});
 afterEach(()=>http.verify());
 it('serializa solo los campos permitidos, sin prioridad ni identidad manipulable',()=>{
  const datos:any={nombreCompleto:' Persona QA ',sucursalId:1,departamentoId:2,contacto:'1234',equipo:'',codigoUsuario:'QA-1',solicitanteId:999,prioridad:'CRITICA',asignadoId:3,sucursalNombre:'Inventada'};
  api.crearSolicitud('T','D',[],datos).subscribe();
  const req=http.expectOne('/api/tickets/solicitudes'),body=req.request.body as FormData;
  expect(body.get('nombreCompleto')).toBe('Persona QA');expect(body.get('sucursalId')).toBe('1');
  for(const key of ['prioridad','solicitanteId','asignadoId','sucursalNombre'])expect(body.has(key)).toBeFalse();
  const grabadas:string[]=[];
  spyOn(Storage.prototype,'setItem').and.callFake((_key:string,value:string)=>{grabadas.push(value);});
  req.flush({id:1,titulo:'T',descripcion:'D',adjuntos:[],datosSolicitante:{nombreCompleto:'SENSIBLE_QA',contacto:'CONTACTO_QA',equipo:'EQUIPO_QA'}});
  expect(grabadas.length).toBeGreaterThan(0);
  expect(grabadas.some(v=>v.includes('SENSIBLE_QA')||v.includes('CONTACTO_QA')||v.includes('EQUIPO_QA'))).toBeFalse();
 });
 it('consulta solo el catálogo mínimo del portal',()=>{
  api.catalogosSolicitud().subscribe();http.expectOne('/api/tickets/solicitudes/catalogos').flush({sucursales:[],departamentos:[]});
 });
});
