import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ProcedimientoService } from './procedimiento.service';

describe('Procedimientos: contrato HTTP de hardware opcional',()=>{
  let api:ProcedimientoService;let http:HttpTestingController;let listaAnterior:string|null;
  const key='dimabug.procedimientos.lista.v1';
  beforeEach(()=>{listaAnterior=localStorage.getItem(key);TestBed.configureTestingModule({imports:[HttpClientTestingModule]});api=TestBed.inject(ProcedimientoService);http=TestBed.inject(HttpTestingController);});
  afterEach(()=>{http.verify();if(listaAnterior===null)localStorage.removeItem(key);else localStorage.setItem(key,listaAnterior);});
  it('envía Sin hardware explícito sin enviar sistema ni módulo',()=>{
    api.crear({nombre:'General',descripcion:null,hardwareId:null,estado:'BORRADOR'}).subscribe(p=>expect(p.hardwareId).toBeNull());
    const req=http.expectOne('/api/procedimientos');expect(req.request.body.hardwareId).toBeNull();expect(Object.hasOwn(req.request.body,'hardwareId')).toBeTrue();expect(Object.hasOwn(req.request.body,'sistemaId')).toBeFalse();expect(Object.hasOwn(req.request.body,'moduloId')).toBeFalse();req.flush({id:7,nombre:'General',estado:'BORRADOR',hardwareId:null});
  });
  it('recupera el hardware al preparar una revisión y permite guardar otro',()=>{
    api.prepararEdicion(7).subscribe(p=>expect(p.hardwareId).toBe(1));http.expectOne('/api/procedimientos/7/edicion').flush({id:8,nombre:'Revisión',estado:'BORRADOR',hardwareId:1,revision:2,origenId:7});
    api.actualizar(8,{nombre:'Revisión',descripcion:null,hardwareId:2}).subscribe(p=>expect(p.hardwareId).toBe(2));const req=http.expectOne('/api/procedimientos/8');expect(req.request.body.hardwareId).toBe(2);req.flush({id:8,nombre:'Revisión',estado:'BORRADOR',hardwareId:2});
  });
  it('el cliente anterior no envía un null implícito que borre el hardware',()=>{
    api.actualizar(7,{nombre:'Solo título',descripcion:null}).subscribe();const req=http.expectOne('/api/procedimientos/7');expect(Object.hasOwn(req.request.body,'hardwareId')).toBeFalse();req.flush({id:7,nombre:'Solo título',estado:'BORRADOR',hardwareId:1});
  });
});
