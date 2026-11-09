import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { MaterialesEjecucionComponent } from './materiales-ejecucion.component';
import { ProcedimientoService } from '../../core/services/procedimiento.service';
import { MaterialPaso } from '../../core/models/procedimiento.model';

describe('Material de apoyo de la revisión usada por una ejecución',()=>{
  let fixture:ComponentFixture<MaterialesEjecucionComponent>;
  let api:jasmine.SpyObj<ProcedimientoService>;
  const video:MaterialPaso={id:5,nombre:'Configuración Login',tipo:'VIDEO',url:'https://drive.google.com/file/d/recurso-sintetico/view'};
  beforeEach(()=>{
    api=jasmine.createSpyObj('procedimientos',['listarMateriales','descargarMaterial']);
    api.listarMateriales.and.returnValue(of([video]));api.descargarMaterial.and.returnValue(of(void 0));
    TestBed.configureTestingModule({imports:[MaterialesEjecucionComponent],providers:[{provide:ProcedimientoService,useValue:api}]});
    fixture=TestBed.createComponent(MaterialesEjecucionComponent);
    fixture.componentRef.setInput('procedimientoId',15);fixture.componentRef.setInput('pasoId',78);
  });
  afterEach(()=>fixture.destroy());
  function mostrar(){fixture.detectChanges();return fixture.nativeElement as HTMLElement;}
  it('consulta los IDs históricos y muestra título, tipo y enlace seguro',()=>{
    const html=mostrar();expect(api.listarMateriales).toHaveBeenCalledOnceWith(15,78);
    expect(html.textContent).toContain('Configuración Login');expect(html.textContent).toContain('Video');
    const a=html.querySelector('a')!;expect(a.href).toBe(video.url);expect(a.target).toBe('_blank');expect(a.rel).toContain('noopener');expect(a.rel).toContain('noreferrer');
    expect(api.descargarMaterial).not.toHaveBeenCalled();
  });
  it('no vuelve a consultar al refrescar la misma vista',()=>{mostrar();fixture.detectChanges();fixture.componentRef.setInput('pasoId',78);fixture.detectChanges();expect(api.listarMateriales).toHaveBeenCalledTimes(1);});
  it('cambiar de paso cancela la respuesta anterior y no mezcla materiales',()=>{
    const antigua=new Subject<MaterialPaso[]>();api.listarMateriales.and.returnValue(antigua);mostrar();
    api.listarMateriales.and.returnValue(of([{...video,id:6,nombre:'Guía del paso siguiente'}]));
    fixture.componentRef.setInput('pasoId',79);fixture.detectChanges();expect(antigua.observed).toBeFalse();antigua.next([video]);
    expect(fixture.nativeElement.textContent).not.toContain('Configuración Login');expect(api.listarMateriales.calls.mostRecent().args).toEqual([15,79]);
  });
  it('cambiar de revisión consulta el ID indicado y no busca la vigente',()=>{mostrar();fixture.componentRef.setInput('procedimientoId',19);fixture.componentRef.setInput('pasoId',90);fixture.detectChanges();expect(api.listarMateriales.calls.mostRecent().args).toEqual([19,90]);});
  it('muestra un estado vacío válido para pasos sin materiales',()=>{api.listarMateriales.and.returnValue(of([]));expect(mostrar().textContent).toContain('no tiene material');});
  it('muestra varios materiales y escapa contenido sin inyectar HTML',()=>{api.listarMateriales.and.returnValue(of([video,{...video,id:6,nombre:'<img src=x onerror=alert(1)>',tipo:'PDF'}]));const html=mostrar();expect(html.querySelectorAll('li').length).toBe(2);expect(html.querySelector('img')).toBeNull();expect(html.textContent).toContain('<img');});
  it('rechaza javascript, data, credenciales y rutas internas',()=>{
    const c=fixture.componentInstance;
    for(const url of ['javascript:alert(1)','data:text/html,malicioso','file:///privado','https://usuario:clave@example.invalid','/ruta-interna'])expect(c.enlace({...video,url})).toBeNull();
  });
  it('descarga recursos privados mediante el servicio autenticado y evita doble clic',()=>{
    const respuesta=new Subject<void>();api.descargarMaterial.and.returnValue(respuesta);const local={...video,tipo:'PDF' as const,url:'/api/procedimientos/15/pasos/78/materiales/5/archivo'};
    api.listarMateriales.and.returnValue(of([local]));const html=mostrar();expect(html.querySelector('a')).toBeNull();const boton=html.querySelector('button')!;boton.click();fixture.componentInstance.descargar(local);expect(api.descargarMaterial).toHaveBeenCalledOnceWith(local);respuesta.complete();expect(fixture.componentInstance.descargando).toBeNull();
  });
  it('permite reintentar errores de consulta sin mostrarlos como resultado vacío',()=>{api.listarMateriales.and.returnValue(throwError(()=>new Error('No autorizado')));const html=mostrar();expect(html.querySelector('[role=alert]')).not.toBeNull();expect(html.textContent).not.toContain('no tiene material');api.listarMateriales.and.returnValue(of([video]));html.querySelector('button')!.click();fixture.detectChanges();expect(html.textContent).toContain('Configuración Login');});
  it('una consulta lenta termina con un error recuperable',fakeAsync(()=>{api.listarMateriales.and.returnValue(new Subject());mostrar();tick(15001);fixture.detectChanges();expect(fixture.componentInstance.cargando).toBeFalse();expect(fixture.nativeElement.textContent).toContain('tardó demasiado');}));
  it('no desborda un contenedor estrecho con títulos largos',()=>{api.listarMateriales.and.returnValue(of([{...video,nombre:'Título'.repeat(50)}]));const html=mostrar();html.style.width='320px';fixture.detectChanges();expect(html.scrollWidth).toBeLessThanOrEqual(html.clientWidth+1);});
});
