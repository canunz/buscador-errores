import {Component,Input,OnChanges,OnDestroy,inject} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Subject,takeUntil,finalize} from 'rxjs';
import {AutoservicioService,MaterialSolucion} from '../../core/services/autoservicio.service';
import {MaterialApoyoFormComponent,MaterialApoyoFormValue} from '../../shared/ui/material-apoyo-form.component';
@Component({selector:'app-materiales-solucion',standalone:true,imports:[FormsModule,MaterialApoyoFormComponent],template:`<section aria-label="Materiales de la solución"><h3>Material de apoyo</h3><p>Solo los materiales autorizados se mostrarán si la solución está activa y habilitada para autoservicio.</p>
@if(error){<p role="alert">{{error}}</p><button type="button" (click)="cargar()" [disabled]="ocupado">Actualizar</button>}
@if(cargando){<p role="status">Cargando materiales…</p>}
<ul>@for(m of items;track m.id){@let enlace=api.enlace(m.url);<li><strong>{{m.nombre}}</strong> · {{m.tipo}}<div>@if(m.url.startsWith('/api/admin/soluciones/')){<button type="button" [disabled]="ocupado" (click)="descargar(m)">Descargar</button>}@else if(enlace){<a [href]="enlace" target="_blank" rel="noopener noreferrer">Abrir</a>}
<button type="button" role="switch" [attr.aria-checked]="m.publicado" [attr.aria-label]="'Publicar material '+m.nombre" [disabled]="ocupado||!activa" (click)="publicar(m)">{{m.publicado?'Autorizado para publicar':'Privado'}}</button><button type="button" [disabled]="ocupado||!activa" (click)="eliminar(m)">Eliminar</button></div></li>}@empty{<li>No hay materiales asociados.</li>}</ul>
@if(activa){@if(!formulario){<button type="button" (click)="formulario=true">Agregar material</button>}@else{<label><input type="checkbox" [(ngModel)]="autorizar" [disabled]="ocupado" /> Autorizar este material para publicación. Confirmo que no contiene información interna o personal.</label><app-material-apoyo-form [enviando]="ocupado" (guardar)="guardar($event)" (cancelar)="formulario=false;autorizar=false" />}}
</section>`,styles:[`section{margin:20px 0}p{font-size:.9rem;color:#5d6c85}ul{list-style:none;padding:0}li{padding:12px 0;border-bottom:1px solid #e4e9f3;overflow-wrap:anywhere}li div{display:flex;flex-wrap:wrap;gap:8px;margin-top:8px}button,a{font:inherit;border:1px solid #becbe1;border-radius:8px;padding:8px 12px;background:white;color:#204caa;cursor:pointer}button:disabled{opacity:.55;cursor:default}button:focus-visible,a:focus-visible{outline:3px solid #739fff}label{display:block;margin:16px 0}button[aria-checked=true]{background:#e8f8ef;color:#166c43}`]})
export class MaterialesSolucionComponent implements OnChanges,OnDestroy {
 @Input({required:true}) solucionId!:number;@Input() activa=true;
 readonly api=inject(AutoservicioService);items:MaterialSolucion[]=[];error='';ocupado=false;cargando=false;formulario=false;autorizar=false;
 private readonly cancelar=new Subject<void>();
 ngOnChanges(){this.cargar();}
 cargar(){this.cancelar.next();this.items=[];this.error='';this.cargando=true;this.api.materiales(this.solucionId,true).pipe(takeUntil(this.cancelar),finalize(()=>this.cargando=false)).subscribe({next:m=>this.items=m,error:(e:Error)=>this.error=e.message});}
 guardar(v:MaterialApoyoFormValue){if(this.ocupado||!this.activa)return;this.ocupado=true;this.error='';this.api.crear(this.solucionId,v,this.autorizar).pipe(takeUntil(this.cancelar),finalize(()=>this.ocupado=false)).subscribe({next:()=>{this.formulario=false;this.autorizar=false;this.cargar();},error:(e:Error)=>this.error=e.message});}
 publicar(m:MaterialSolucion){if(this.ocupado||!this.activa)return;this.ocupado=true;this.api.publicar(this.solucionId,m).pipe(takeUntil(this.cancelar),finalize(()=>this.ocupado=false)).subscribe({next:()=>this.cargar(),error:(e:Error)=>this.error=e.message});}
 eliminar(m:MaterialSolucion){if(this.ocupado||!this.activa)return;this.ocupado=true;this.api.eliminar(this.solucionId,m).pipe(takeUntil(this.cancelar),finalize(()=>this.ocupado=false)).subscribe({next:()=>this.cargar(),error:(e:Error)=>this.error=e.message});}
 descargar(m:MaterialSolucion){if(this.ocupado)return;this.ocupado=true;this.api.descargar(this.solucionId,m).pipe(takeUntil(this.cancelar),finalize(()=>this.ocupado=false)).subscribe({error:(e:Error)=>this.error=e.message});}
 ngOnDestroy(){this.cancelar.next();this.cancelar.complete();}
}
