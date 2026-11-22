import {Component,OnInit,OnDestroy,inject} from '@angular/core';
import {FormsModule} from '@angular/forms';import {ActivatedRoute,RouterLink} from '@angular/router';
import {MaterialesPublicosComponent} from '../../shared/ui/materiales-publicos.component';
import {Subject,takeUntil,finalize} from 'rxjs';
import {AutoservicioService,SolucionPublica} from '../../core/services/autoservicio.service';
@Component({selector:'app-portal-soluciones',standalone:true,imports:[FormsModule,RouterLink,MaterialesPublicosComponent],templateUrl:'./portal-soluciones.component.html',styleUrl:'./portal-soluciones.component.css'})
export class PortalSolucionesComponent implements OnInit,OnDestroy {
 readonly api=inject(AutoservicioService);private readonly route=inject(ActivatedRoute);
 private readonly cancelar=new Subject<void>();private readonly destruir=new Subject<void>();
 q='';busquedaHecha='';items:SolucionPublica[]=[];detalle:SolucionPublica|null=null;cargando=false;error='';id=0;esDetalle=false;noDisponible=false;
 ngOnInit(){this.route.paramMap.pipe(takeUntil(this.destruir)).subscribe(p=>{this.esDetalle=p.has('id');this.id=Number(p.get('id'));this.noDisponible=false;this.detalle=null;this.buscar();});}
 buscar(){this.cancelar.next();this.error='';this.cargando=true;
  if(this.esDetalle&&(!Number.isSafeInteger(this.id)||this.id<1)){this.cargando=false;this.noDisponible=true;this.error='El recurso no está disponible.';return;}
  if(this.esDetalle){this.api.detalle(this.id).pipe(takeUntil(this.cancelar),finalize(()=>this.cargando=false)).subscribe({next:d=>{this.detalle=d;},error:(e:Error)=>{this.detalle=null;this.noDisponible=true;this.error='El recurso no está disponible. Comprueba el enlace o intenta más tarde.';}});}
  else{this.busquedaHecha=this.q.trim();this.api.buscar(this.q.trim()).pipe(takeUntil(this.cancelar),finalize(()=>this.cargando=false)).subscribe({next:items=>this.items=items,error:(e:Error)=>{this.items=[];this.error=e.message;}});}
 }
 /** Texto corto para la tarjeta: une las líneas y corta en 160 caracteres. */
 resumen(texto:string):string{
  const plano=(texto??'').split(/\r?\n/).map(l=>l.replace(/^\s*(\d+[.)-]|[-•*])\s*/,'').trim()).filter(Boolean).join(' · ');
  return plano.length>160?plano.slice(0,160).trimEnd()+'…':plano;
 }
 /** Separa las instrucciones por línea para mostrarlas como pasos; quita numeraciones o viñetas que ya traigan. */
 get pasos():string[]{
  const texto=this.detalle?.instrucciones??'';
  return texto.split(/\r?\n/).map(l=>l.replace(/^\s*(\d+[.)-]|[-•*])\s*/,'').trim()).filter(l=>l.length>0);
 }
 /** Pasos para el detalle: si las instrucciones vienen en una sola línea, igual se muestran como el paso 1. */
 get pasosDetalle():string[]{
  const pasos=this.pasos;
  if(pasos.length)return pasos;
  const texto=(this.detalle?.instrucciones??'').trim();
  return texto?[texto]:[];
 }
 ngOnDestroy(){this.cancelar.next();this.cancelar.complete();this.destruir.next();this.destruir.complete();}
}
