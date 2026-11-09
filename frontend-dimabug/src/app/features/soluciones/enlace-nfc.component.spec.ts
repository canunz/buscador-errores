import {TestBed} from '@angular/core/testing';
import {Clipboard} from '@angular/cdk/clipboard';import {of,Subject,throwError} from 'rxjs';
import {EnlaceNfcComponent,basePortalNfc,urlSolucionNfc} from './enlace-nfc.component';
import {AutoservicioService} from '../../core/services/autoservicio.service';
import {environment} from '../../../environments/environment';
describe('Enlace público individual para NFC y QR',()=>{
 let api:jasmine.SpyObj<AutoservicioService>;let clipboard:jasmine.SpyObj<Clipboard>;let original:string;let saved:string|null;
 beforeEach(()=>{original=environment.publicPortalUrl;environment.publicPortalUrl='';saved=localStorage.getItem('dimabug.portal.publicBase');localStorage.removeItem('dimabug.portal.publicBase');
  api=jasmine.createSpyObj('api',['detalle']);api.detalle.and.returnValue(of({id:8,titulo:'Tambor impresora',instrucciones:'Instrucciones públicas',tipo:'PASOS'}));clipboard=jasmine.createSpyObj('clipboard',['copy']);clipboard.copy.and.returnValue(true);
  TestBed.configureTestingModule({imports:[EnlaceNfcComponent],providers:[{provide:AutoservicioService,useValue:api},{provide:Clipboard,useValue:clipboard}]});
 });
 afterEach(()=>{environment.publicPortalUrl=original;if(saved===null)localStorage.removeItem('dimabug.portal.publicBase');else localStorage.setItem('dimabug.portal.publicBase',saved);});
 it('genera un identificador estable por solución sin asumir un conocimiento único',()=>{expect(urlSolucionNfc('https://soporte.example.invalid/',8)).toBe('https://soporte.example.invalid/autoservicio/soluciones/8');expect(urlSolucionNfc('https://soporte.example.invalid/app',9)).toBe('https://soporte.example.invalid/app/autoservicio/soluciones/9');});
 it('rechaza localhost, loopback, credenciales y destinos con parámetros',()=>{for(const base of ['http://localhost:4200','http://equipo.localhost','http://127.0.0.1:4200','http://2130706433','http://[::1]','http://0.0.0.0','javascript:alert(1)','https://usuario:clave@example.invalid','https://portal.example.invalid?','https://portal.example.invalid/#fragmento'])expect(basePortalNfc(base)).toBeNull();});
 it('acepta una IP de red configurada sin inventar una dirección definitiva',()=>{expect(urlSolucionNfc('http://192.0.2.10:4200',8)).toBe('http://192.0.2.10:4200/autoservicio/soluciones/8');});
 it('comprueba la publicación y copia una ruta de prueba si aún no hay dominio',()=>{
  const f=TestBed.createComponent(EnlaceNfcComponent);f.componentRef.setInput('solucionId',8);f.detectChanges();expect(api.detalle).toHaveBeenCalledOnceWith(8);
  expect(f.nativeElement.textContent).toContain('Copiar enlace');f.componentInstance.copiar();expect(clipboard.copy).toHaveBeenCalledWith('/autoservicio/soluciones/8');expect(f.componentInstance.aviso).toContain('Ruta de prueba');expect(f.componentInstance.url).not.toContain('localhost');f.destroy();
 });
 it('la base de despliegue produce una URL completa, sin JWT',()=>{environment.publicPortalUrl='https://soporte.example.invalid';const f=TestBed.createComponent(EnlaceNfcComponent);f.componentRef.setInput('solucionId',8);f.detectChanges();f.componentInstance.copiar();expect(clipboard.copy).toHaveBeenCalledWith('https://soporte.example.invalid/autoservicio/soluciones/8');f.destroy();});
 it('una solución privada o deshabilitada no ofrece copiar enlace',()=>{api.detalle.and.returnValue(throwError(()=>new Error('No disponible')));const f=TestBed.createComponent(EnlaceNfcComponent);f.componentRef.setInput('solucionId',8);f.detectChanges();expect(f.nativeElement.querySelector('button')).toBeNull();f.componentInstance.copiar();expect(clipboard.copy).not.toHaveBeenCalled();f.destroy();});
 it('espera publicación y comunica un fallo de portapapeles sin declarar éxito',()=>{const s=new Subject<any>();api.detalle.and.returnValue(s);clipboard.copy.and.returnValue(false);const f=TestBed.createComponent(EnlaceNfcComponent);f.componentRef.setInput('solucionId',8);f.detectChanges();expect(f.componentInstance.url).toBeNull();s.next({id:8});s.complete();f.componentInstance.copiar();expect(f.componentInstance.aviso).toContain('manualmente');f.destroy();});
});
