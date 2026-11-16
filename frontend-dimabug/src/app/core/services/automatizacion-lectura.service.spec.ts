import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { AutomatizacionEjecucionService } from './automatizacion-ejecucion.service';

describe('Automatización: lecturas acotadas sin efectos de envío',()=>{
  let api:AutomatizacionEjecucionService;let http:HttpTestingController;
  beforeEach(()=>{TestBed.configureTestingModule({imports:[HttpClientTestingModule]});api=TestBed.inject(AutomatizacionEjecucionService);http=TestBed.inject(HttpTestingController);});
  afterEach(()=>http.verify());
  it('agota el plazo, cancela el GET y no inicia un envío',fakeAsync(()=>{
    let mensaje='';api.estado(21).subscribe({error:e=>mensaje=e.message});const req=http.expectOne('/api/ejecuciones/21/automatizacion');tick(15000);expect(req.cancelled).toBeTrue();expect(mensaje).toContain('tardó demasiado');http.expectNone(r=>r.method==='POST');
  }));
  it('acepta una respuesta lenta dentro del límite',fakeAsync(()=>{
    let recibida=false;api.estado(21).subscribe(()=>recibida=true);const req=http.expectOne('/api/ejecuciones/21/automatizacion');tick(14999);req.flush({solicitudes:[],actividad:[],disponible:true,simulado:true});expect(recibida).toBeTrue();tick(1);
  }));
  it('distingue el fallo de conexión de una solicitud pendiente',()=>{
    let error='';api.estado(21).subscribe({next:()=>fail('No debe fabricar un estado vacío'),error:e=>error=e.message});http.expectOne('/api/ejecuciones/21/automatizacion').error(new ProgressEvent('error'));expect(error).toContain('conectar');
  });
  it('un fallo de BD permite recuperar el estado mediante otro GET',()=>{
    api.estado(21).subscribe({error:e=>expect(e.message).toBeTruthy()});http.expectOne('/api/ejecuciones/21/automatizacion').flush({message:'Base no disponible'},{status:503,statusText:'Service Unavailable'});
    api.estado(21).subscribe(s=>expect(s.solicitudes).toEqual([]));http.expectOne('/api/ejecuciones/21/automatizacion').flush({solicitudes:[],actividad:[],disponible:true,simulado:true});http.expectNone(r=>r.method==='POST');
  });
});
