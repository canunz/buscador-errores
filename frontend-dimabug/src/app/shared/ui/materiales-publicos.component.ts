import {Component,Input,OnChanges,OnDestroy,inject} from '@angular/core';
import {Subject,takeUntil,finalize} from 'rxjs';
import {AutoservicioService,MaterialSolucion} from '../../core/services/autoservicio.service';
import {apiUrl} from '../../core/config/api';

@Component({selector:'app-materiales-publicos',standalone:true,template:`
<section aria-label="Material de apoyo" [attr.aria-busy]="cargando">
 @if(cargando){<p role="status">Consultando materiales de apoyo…</p>}
 @if(error){<p role="alert">{{error}}</p><button type="button" (click)="cargar()" [disabled]="cargando">Reintentar materiales</button>}
 @if(!cargando&&!error){
  @if(plegable&&materiales.length){<button type="button" [attr.aria-expanded]="abierto" (click)="abierto=!abierto">{{abierto?'Ocultar material de apoyo':'Ver material de apoyo'}}</button>}
  @if(!plegable||abierto){<ul>
   @for(m of materiales;track m.id){@let url=enlace(m);<li><span><strong>{{m.nombre}}</strong> · {{m.tipo}}</span>@if(url){<a [href]="url" target="_blank" rel="noopener noreferrer">Abrir recurso</a>}</li>}
   @empty{<li>No hay materiales públicos para esta solución.</li>}
  </ul>}
  @else if(!materiales.length){<p>No hay materiales públicos para esta solución.</p>}
 }
</section>`,styles:[`
:host{display:block;min-width:0}button,a{font:inherit;color:#2258d5;border:1px solid #becbe1;border-radius:8px;padding:8px 12px;background:white;cursor:pointer;display:inline-block}button:disabled{opacity:.55;cursor:default}a{text-decoration:none}a:focus-visible,button:focus-visible{outline:3px solid #6397ef;outline-offset:3px}ul{list-style:none;padding:0}li{padding:12px 0;display:flex;flex-wrap:wrap;gap:12px;justify-content:space-between;align-items:center;border-bottom:1px solid #e2e8f4;overflow-wrap:anywhere}p{font-size:.9rem} @media(max-width:600px){li{align-items:flex-start;flex-direction:column}}
`]})
export class MaterialesPublicosComponent implements OnChanges,OnDestroy {
 @Input({required:true}) solucionId!:number;
 @Input() plegable=false;
 readonly api=inject(AutoservicioService);
 materiales:MaterialSolucion[]=[];cargando=false;error='';abierto=false;
 private readonly cancelar=new Subject<void>();
 ngOnChanges(){this.abierto=false;this.cargar();}
 cargar(){
  this.cancelar.next();this.materiales=[];this.error='';
  if(!Number.isSafeInteger(this.solucionId)||this.solucionId<1)return;
  this.cargando=true;
  this.api.materiales(this.solucionId).pipe(takeUntil(this.cancelar),finalize(()=>this.cargando=false)).subscribe({
   next:items=>this.materiales=items.filter(m=>m.publicado),
   error:()=>this.error='No fue posible cargar los materiales de apoyo. Intenta nuevamente.'
  });
 }
 enlace(m:MaterialSolucion):string|null{
  const path='/publico/soluciones/'+this.solucionId+'/materiales/'+m.id+'/archivo';
  return m.url==='/api'+path?apiUrl(path):this.api.enlace(m.url);
 }
 ngOnDestroy(){this.cancelar.next();this.cancelar.complete();}
}
