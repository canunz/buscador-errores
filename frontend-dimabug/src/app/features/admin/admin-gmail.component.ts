import { Component, OnInit, HostListener, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { A11yModule } from '@angular/cdk/a11y';
import { DatePipe } from '@angular/common';
import { finalize, forkJoin, catchError, of } from 'rxjs';
import { EstadoGmail, EstadoPruebaGmail, ResultadoPruebaGmail, GmailAdminService } from '../../core/services/gmail-admin.service';
@Component({selector:'app-admin-gmail',standalone:true,imports:[RouterLink,DatePipe,A11yModule],
  styleUrls:['../tickets/tickets-comun.css','./admin-gmail.component.css'],templateUrl:'./admin-gmail.component.html'})
export class AdminGmailComponent implements OnInit {
  private readonly api=inject(GmailAdminService);
  estado:EstadoGmail|null=null;error='';ocupado=false;cargando=false;pedirRevocacion=false;operacion:'autorizar'|'revocar'|'prueba'|null=null;
  prueba:EstadoPruebaGmail|null=null;errorPrueba='';confirmarPrueba=false;private clavePrueba:string|null=null;
  ngOnInit(){this.cargar();}
  cargar(){if(this.cargando || this.ocupado)return;this.cargando=true;this.error='';this.errorPrueba='';forkJoin({cuenta:this.api.estado(),prueba:this.api.pruebaEstado().pipe(catchError((e:Error)=>{this.errorPrueba=e.message;return of(null);}))}).pipe(finalize(()=>this.cargando=false)).subscribe({next:r=>{this.estado=r.cuenta;this.prueba=r.prueba;},error:(e:Error)=>this.error=e.message});}
  conectar(){if(this.ocupado || !this.estado?.configurado || this.estado.conectado)return;this.ocupado=true;this.operacion='autorizar';this.error='';this.api.autorizar().pipe(finalize(()=>{this.ocupado=false;this.operacion=null;})).subscribe({next:r=>{
    try{const u=new URL(r.authorizationUrl);if(u.origin!=='https://accounts.google.com' || u.pathname!=='/o/oauth2/v2/auth'){this.error='La dirección de autorización no es válida.';return;}window.location.assign(u.href);}catch{this.error='La dirección de autorización no es válida.';}
  },error:(e:Error)=>this.error=e.message});}
  revocar(){if(this.ocupado || !this.pedirRevocacion)return;this.ocupado=true;this.operacion='revocar';this.error='';this.api.revocar().pipe(finalize(()=>{this.ocupado=false;this.operacion=null;})).subscribe({next:e=>{this.estado=e;this.pedirRevocacion=false;},error:(e:Error)=>{this.error=e.message;this.estado=null;}});}
  cerrarConfirmacion(){if(!this.ocupado)this.pedirRevocacion=false;}
  get puedeProbar(){return !this.ocupado && !this.cargando && !!this.prueba?.habilitado && (!this.prueba.ultima || this.prueba.ultima.estado==='FALLIDO');}
  abrirPrueba(){if(!this.puedeProbar)return;this.clavePrueba=crypto.randomUUID();this.confirmarPrueba=true;this.errorPrueba='';}
  cerrarPrueba(){if(!this.ocupado)this.confirmarPrueba=false;}
  enviarPrueba(){if(!this.confirmarPrueba || !this.clavePrueba || !this.puedeProbar || !this.prueba)return;this.ocupado=true;this.operacion='prueba';this.errorPrueba='';
    this.api.enviarPrueba({clave:this.clavePrueba,anterior:this.prueba.ultima?.id??null,confirmado:true,destinatario:this.prueba.destinatario}).pipe(finalize(()=>{this.ocupado=false;this.operacion=null;})).subscribe({next:r=>{if(this.prueba)this.prueba={...this.prueba,ultima:r};this.confirmarPrueba=false;},error:()=>{this.errorPrueba='No se confirmó el resultado. Actualiza el estado antes de reintentar; el servidor podría haber aceptado el mensaje.';this.confirmarPrueba=false;this.prueba=null;}});
  }
  etiquetaPrueba(p:ResultadoPruebaGmail){return ({PENDIENTE:'Pendiente',ENVIANDO:'Enviando',ENVIADO:'Google aceptó el mensaje; entrega en bandeja de entrada no confirmada',FALLIDO:'Fallido: Google no aceptó el envío o no pudo iniciarse',INCIERTO:'Resultado incierto: no reintentar automáticamente'})[p.estado];}
  @HostListener('document:keydown.escape') escape(){this.cerrarConfirmacion();this.cerrarPrueba();}
}
