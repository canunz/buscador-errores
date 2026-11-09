import {TestBed, fakeAsync, tick} from '@angular/core/testing';
import {provideRouter, Router} from '@angular/router';
import {of, throwError} from 'rxjs';
import {LoginComponent} from './login.component';
import {AuthService} from '../../../core/services/auth.service';

describe('Integración del ingreso por código y autoservicio',()=>{
 let auth:jasmine.SpyObj<AuthService>;
 beforeEach(()=>{
  auth=jasmine.createSpyObj('AuthService',['solicitarCodigoAcceso','verificarCodigoAcceso']);
  auth.solicitarCodigoAcceso.and.returnValue(of({message:'Enviado',segundosVigencia:300,segundosReenvio:60}));
  auth.verificarCodigoAcceso.and.returnValue(of({token:"token-sintetico",usuario:{usuarioId:1,usuarioNombre:"QA",usuarioEmail:"qa@example.invalid",usuarioEstado:true}}));
  TestBed.configureTestingModule({imports:[LoginComponent],providers:[provideRouter([]),{provide:AuthService,useValue:auth}]});
 });
 it('conserva el enlace público sin sustituir el formulario de código por contraseña',()=>{
  const f=TestBed.createComponent(LoginComponent);f.detectChanges();
  expect(f.nativeElement.querySelector('a[href="/autoservicio"]')).not.toBeNull();
  expect(f.nativeElement.querySelector('input[type=password]')).toBeNull();
  expect(f.nativeElement.textContent).toContain('Enviar código de acceso');
 });
 it('solicita un código, muestra su campo y verifica antes de navegar',fakeAsync(()=>{
  const f=TestBed.createComponent(LoginComponent);const c=f.componentInstance;
  const navegar=spyOn(TestBed.inject(Router),'navigateByUrl').and.resolveTo(true);
  c.form.setValue({email:'qa@example.invalid'});c.enviarCodigo();f.detectChanges();tick();
  expect(auth.solicitarCodigoAcceso).toHaveBeenCalledOnceWith('qa@example.invalid');
  expect(f.nativeElement.querySelector('input[autocomplete=one-time-code]')).not.toBeNull();
  expect(navegar).not.toHaveBeenCalled();c.codigoForm.setValue({codigo:'123456'});c.verificar();
  expect(auth.verificarCodigoAcceso).toHaveBeenCalledOnceWith('qa@example.invalid','123456');
  expect(navegar).toHaveBeenCalledOnceWith('/inicio');f.destroy();tick();
 }));
 it('un error al solicitar el código permite corregir sin navegar',()=>{
  auth.solicitarCodigoAcceso.and.returnValue(throwError(()=>new Error('No disponible')));
  const f=TestBed.createComponent(LoginComponent);f.componentInstance.form.setValue({email:'qa@example.invalid'});
  f.componentInstance.enviarCodigo();f.detectChanges();
  expect(f.nativeElement.querySelector('[role=alert]').textContent).toContain('No disponible');
  expect(f.componentInstance.loading).toBeFalse();expect(auth.verificarCodigoAcceso).not.toHaveBeenCalled();
 });
});
