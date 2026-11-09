import {Component,OnInit,OnDestroy,inject} from '@angular/core';
import {FormsModule} from '@angular/forms';import {ActivatedRoute,RouterLink} from '@angular/router';
import {apiUrl} from '../../core/config/api';
import {Subject,takeUntil,finalize} from 'rxjs';
import {AutoservicioService,SolucionPublica,MaterialSolucion} from '../../core/services/autoservicio.service';
@Component({selector:'app-portal-soluciones',standalone:true,imports:[FormsModule,RouterLink],templateUrl:'./portal-soluciones.component.html',styleUrls:['../tickets/tickets-comun.css','./portal-soluciones.component.css']})
export class PortalSolucionesComponent implements OnInit,OnDestroy {
 readonly api=inject(AutoservicioService);private readonly route=inject(ActivatedRoute);
 private readonly cancelar=new Subject<void>();private readonly destruir=new Subject<void>();
 q='';items:SolucionPublica[]=[];detalle:SolucionPublica|null=null;materiales:MaterialSolucion[]=[];cargando=false;error='';errorMateriales='';id=0;esDetalle=false;noDisponible=false;
 ngOnInit(){this.route.paramMap.pipe(takeUntil(this.destruir)).subscribe(p=>{this.esDetalle=p.has('id');this.id=Number(p.get('id'));this.noDisponible=false;this.detalle=null;this.materiales=[];this.buscar();});}
 buscar(){this.cancelar.next();this.error='';this.errorMateriales='';this.cargando=true;
  if(this.esDetalle&&(!Number.isSafeInteger(this.id)||this.id<1)){this.cargando=false;this.noDisponible=true;this.error='El recurso no está disponible.';return;}
  if(this.esDetalle){this.api.detalle(this.id).pipe(takeUntil(this.cancelar),finalize(()=>this.cargando=false)).subscribe({next:d=>{this.detalle=d;this.cargarMateriales();},error:(e:Error)=>{this.detalle=null;this.noDisponible=true;this.error='El recurso no está disponible. Comprueba el enlace o intenta más tarde.';}});}
  else{this.api.buscar(this.q.trim()).pipe(takeUntil(this.cancelar),finalize(()=>this.cargando=false)).subscribe({next:items=>this.items=items,error:(e:Error)=>{this.items=[];this.error=e.message;}});}
 }
 cargarMateriales(){this.api.materiales(this.id).pipe(takeUntil(this.cancelar)).subscribe({next:m=>this.materiales=m,error:(e:Error)=>{this.materiales=[];this.errorMateriales=e.message;}});}
 archivo(m:MaterialSolucion):string|null{const path='/publico/soluciones/'+this.id+'/materiales/'+m.id+'/archivo';return m.url==='/api'+path?apiUrl(path):null;}
 ngOnDestroy(){this.cancelar.next();this.cancelar.complete();this.destruir.next();this.destruir.complete();}
}
