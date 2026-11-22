import {Component,Input,OnChanges,OnDestroy,inject} from '@angular/core';
import {Subject,takeUntil,finalize} from 'rxjs';
import {AutoservicioService,MaterialSolucion} from '../../core/services/autoservicio.service';
import {apiUrl} from '../../core/config/api';
import {AvisoErrorComponent} from './aviso-error.component';

/**
 * Muestra el material de apoyo publicado de una solución.
 * Tiene dos formas de verse:
 *  - "lista": filas simples (la usa el chatbot, que tiene poco espacio).
 *  - "galeria": carpetas por tipo (videos, imágenes, documentos, enlaces) con tarjetas
 *    y miniaturas. Los videos de Drive o YouTube muestran su miniatura con un botón de play.
 */
@Component({selector:'app-materiales-publicos',standalone:true,imports:[AvisoErrorComponent],template:`
<section aria-label="Material de apoyo" [attr.aria-busy]="cargando">
 @if(cargando){<p role="status">Consultando materiales de apoyo…</p>}
 @if(error){<app-aviso-error [mensaje]="error" etiqueta="Reintentar materiales" [ocupado]="cargando" (reintentar)="cargar()" />}
 @if(!cargando&&!error){
  @if(vista==='galeria'){
   @if(!materiales.length){<p class="vacio">No hay materiales públicos para esta solución.</p>}
   <!-- El material se ordena por carpetas según su tipo. Si una carpeta tiene más de 3 elementos, parte cerrada. -->
   <div class="carpetas">
    @for(g of grupos;track g.tipo){
     @let abierta=estaAbierta(g);
     <div class="carpeta" [attr.data-tipo]="g.tipo">
      @if(g.items.length>maximoAbierto){
       <button type="button" class="carpeta-cabecera plegable" [attr.aria-expanded]="abierta" (click)="alternar(g.tipo)">
        <span class="carpeta-icono" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/></svg></span>
        <span class="carpeta-texto"><strong>{{ g.titulo }}</strong><small>{{ g.items.length }} {{ g.unidad }}</small></span>
        <span class="carpeta-accion">{{ abierta ? 'Cerrar carpeta' : 'Abrir carpeta' }}
         <svg class="chevron" [class.girado]="abierta" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M6 9l6 6 6-6"/></svg>
        </span>
       </button>
      } @else {
       <div class="carpeta-cabecera">
        <span class="carpeta-icono" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/></svg></span>
        <span class="carpeta-texto"><strong>{{ g.titulo }}</strong><small>{{ g.items.length }} {{ g.items.length === 1 ? g.unidadUno : g.unidad }}</small></span>
       </div>
      }
      @if(abierta){
       <div class="galeria">
        @for(m of g.items;track m.id){
         @let url=enlace(m);
         @let mini=miniatura(m);
         <a class="material" [class.compacta]="m.tipo==='PDF'||m.tipo==='ENLACE'" [class.sin-enlace]="!url" [attr.href]="url" target="_blank" rel="noopener noreferrer">
          <div class="portada" [attr.data-tipo]="m.tipo">
           @if(mini&&!sinMiniatura.has(m.id)){
            <img [src]="mini" alt="" loading="lazy" referrerpolicy="no-referrer" (error)="sinMiniatura.add(m.id)" />
           } @else {
            <span class="icono" aria-hidden="true">
             @switch(m.tipo){
              @case('VIDEO'){<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="2" y="5" width="15" height="14" rx="2"/><path d="M17 10l5-3v10l-5-3z"/></svg>}
              @case('PDF'){<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6M8 13h8M8 17h5"/></svg>}
              @case('IMAGEN'){<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/></svg>}
              @default{<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>}
             }
            </span>
           }
           @if(m.tipo==='VIDEO'){<span class="play" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z" fill="currentColor"/></svg></span>}
          </div>
          <div class="info">
           <span class="etiqueta">{{ nombreTipo(m.tipo) }}</span>
           <strong>{{ m.nombre }}</strong>
           @if(url){<span class="accion">{{ accion(m.tipo) }} →</span>}
          </div>
         </a>
        }
       </div>
      }
     </div>
    }
   </div>
  } @else {
   @if(plegable&&materiales.length){<button type="button" [attr.aria-expanded]="abierto" (click)="abierto=!abierto">{{abierto?'Ocultar material de apoyo':'Ver material de apoyo'}}</button>}
   @if(!plegable||abierto){<ul>
    @for(m of materiales;track m.id){@let url=enlace(m);<li><span><strong>{{m.nombre}}</strong> · {{m.tipo}}</span>@if(url){<a [href]="url" target="_blank" rel="noopener noreferrer">Abrir recurso</a>}</li>}
    @empty{<li>No hay materiales públicos para esta solución.</li>}
   </ul>}
   @else if(!materiales.length){<p>No hay materiales públicos para esta solución.</p>}
  }
 }
</section>`,styles:[`
:host{display:block;min-width:0}button,a{font:inherit;color:#2258d5;border:1px solid #becbe1;border-radius:8px;padding:8px 12px;background:white;cursor:pointer;display:inline-block}button:disabled{opacity:.55;cursor:default}a{text-decoration:none}a:focus-visible,button:focus-visible{outline:3px solid #6397ef;outline-offset:3px}ul{list-style:none;padding:0}li{padding:12px 0;display:flex;flex-wrap:wrap;gap:12px;justify-content:space-between;align-items:center;border-bottom:1px solid #e2e8f4;overflow-wrap:anywhere}p{font-size:.9rem} @media(max-width:600px){li{align-items:flex-start;flex-direction:column}}

/* Galería: tarjetas con miniatura */
.galeria{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,220px),1fr));gap:14px}
.galeria .material{display:flex;flex-direction:column;padding:0;border:1px solid #e3e8f2;border-radius:16px;overflow:hidden;background:#fff;color:inherit;box-shadow:0 8px 20px rgba(15,23,42,.05);transition:transform .15s ease,box-shadow .15s ease}
.galeria .material:hover{transform:translateY(-2px);box-shadow:0 14px 28px rgba(15,23,42,.1)}
.galeria .material.sin-enlace{cursor:default}
.galeria .material.sin-enlace:hover{transform:none;box-shadow:0 8px 20px rgba(15,23,42,.05)}
.portada{position:relative;aspect-ratio:16/9;background:linear-gradient(135deg,#eaf0ff,#f6f8fd);display:flex;align-items:center;justify-content:center;overflow:hidden}
.portada[data-tipo=VIDEO]{background:linear-gradient(135deg,#0b1b4d,#1e4fd6)}
.portada img{width:100%;height:100%;object-fit:cover;display:block}
.portada .icono{width:44px;height:44px;color:#1e4fd6}
.portada[data-tipo=VIDEO] .icono{color:rgba(255,255,255,.75)}
.portada .icono svg{width:100%;height:100%}
.portada .play{position:absolute;width:52px;height:52px;border-radius:50%;background:rgba(255,255,255,.95);color:#1e4fd6;display:flex;align-items:center;justify-content:center;box-shadow:0 8px 22px rgba(0,0,0,.25);transition:transform .15s ease}
.portada .play svg{width:24px;height:24px;margin-left:3px}
.galeria .material:hover .play{transform:scale(1.08)}
.info{display:flex;flex-direction:column;gap:4px;padding:14px 16px 16px;overflow-wrap:anywhere}
.info .etiqueta{font-size:11px;font-weight:800;letter-spacing:.06em;text-transform:uppercase;color:#1e4fd6}
.info strong{color:#0b1b4d;font-size:.98rem;line-height:1.35}
.info .accion{margin-top:6px;font-size:.88rem;font-weight:700;color:#1e4fd6}
.vacio{margin:0;color:#64748b}

/* Carpetas por tipo de material */
.carpetas{display:grid;gap:14px}
.carpeta{border:1px solid #e3e8f2;border-radius:16px;background:#fbfcff;overflow:hidden}
.carpeta-cabecera{display:flex;align-items:center;gap:14px;width:100%;padding:14px 16px;border:0;border-radius:0;background:transparent;color:#0b1b4d;text-align:left}
.carpeta-cabecera.plegable{cursor:pointer}
.carpeta-cabecera.plegable:hover{background:#f2f6ff}
.carpeta-icono{flex-shrink:0;width:42px;height:42px;border-radius:12px;display:flex;align-items:center;justify-content:center;background:#eaf0ff;color:#1e4fd6}
.carpeta-icono svg{width:24px;height:24px}
.carpeta[data-tipo=VIDEO] .carpeta-icono{background:#ede9fe;color:#6d28d9}
.carpeta[data-tipo=IMAGEN] .carpeta-icono{background:#e0f2fe;color:#0369a1}
.carpeta[data-tipo=PDF] .carpeta-icono{background:#fee2e2;color:#b91c1c}
.carpeta[data-tipo=ENLACE] .carpeta-icono{background:#dcfce7;color:#15803d}
.carpeta-texto{display:flex;flex-direction:column;gap:2px;min-width:0;flex:1}
.carpeta-texto strong{font-size:1rem}
.carpeta-texto small{color:#64748b;font-size:.85rem}
.carpeta-accion{display:inline-flex;align-items:center;gap:6px;color:#1e4fd6;font-weight:700;font-size:.88rem;white-space:nowrap}
.chevron{width:18px;height:18px;transition:transform .2s ease}
.chevron.girado{transform:rotate(180deg)}
.carpeta .galeria{padding:4px 16px 16px}
/* Documentos y enlaces no tienen portada: se muestran como filas compactas con su ícono. */
.galeria .material.compacta{flex-direction:row;align-items:center}
.material.compacta .portada{aspect-ratio:auto;width:52px;height:52px;margin:12px 0 12px 12px;border-radius:12px;flex-shrink:0}
.material.compacta .portada .icono{width:26px;height:26px}
.material.compacta .info{padding:12px 14px;gap:2px}
.material.compacta .etiqueta{display:none}
@media(max-width:600px){.carpeta-accion{font-size:0;gap:0}.carpeta .galeria{padding:4px 10px 12px}.galeria{grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.info{padding:10px 12px 12px}.info strong{font-size:.88rem}.info .accion{font-size:.8rem}.portada .play{width:40px;height:40px}.portada .play svg{width:18px;height:18px}.portada .icono{width:32px;height:32px}}
`]})
export class MaterialesPublicosComponent implements OnChanges,OnDestroy {
 @Input({required:true}) solucionId!:number;
 @Input() plegable=false;
 /** "lista" para espacios pequeños; "galeria" para mostrar tarjetas con miniatura. */
 @Input() vista:'lista'|'galeria'='lista';
 readonly api=inject(AutoservicioService);
 materiales:MaterialSolucion[]=[];cargando=false;error='';abierto=false;
 /** Una carpeta con más de esta cantidad de elementos parte cerrada. */
 readonly maximoAbierto=3;
 /** Carpetas que el usuario abrió a mano. */
 readonly abiertas=new Set<string>();
 /** Materiales cuya miniatura no se pudo cargar (por ejemplo, un Drive privado); se muestra el ícono en su lugar. */
 readonly sinMiniatura=new Set<number>();
 private readonly cancelar=new Subject<void>();
 ngOnChanges(){this.abierto=false;this.abiertas.clear();this.cargar();}

 /** Agrupa el material por tipo, en este orden: videos, imágenes, documentos y enlaces. */
 get grupos():{tipo:string;titulo:string;unidad:string;unidadUno:string;items:MaterialSolucion[]}[]{
  const carpetas=[
   {tipo:'VIDEO',titulo:'Videos',unidad:'videos',unidadUno:'video'},
   {tipo:'IMAGEN',titulo:'Imágenes',unidad:'imágenes',unidadUno:'imagen'},
   {tipo:'PDF',titulo:'Documentos',unidad:'documentos',unidadUno:'documento'},
   {tipo:'ENLACE',titulo:'Enlaces',unidad:'enlaces',unidadUno:'enlace'},
  ];
  return carpetas
   .map(c=>({...c,items:this.materiales.filter(m=>m.tipo===c.tipo)}))
   .filter(c=>c.items.length>0);
 }

 /** Las carpetas chicas se ven siempre abiertas; las grandes, solo si el usuario las abre. */
 estaAbierta(g:{tipo:string;items:MaterialSolucion[]}):boolean{
  return g.items.length<=this.maximoAbierto||this.abiertas.has(g.tipo);
 }

 alternar(tipo:string):void{
  if(this.abiertas.has(tipo)){this.abiertas.clear();return;}
  this.abiertas.clear();
  this.abiertas.add(tipo);
 }

 cargar(){
  this.cancelar.next();this.materiales=[];this.error='';this.sinMiniatura.clear();
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

 /**
  * Busca una imagen de portada para el material:
  *  - Google Drive: usa la miniatura que genera Drive (el archivo debe estar compartido con "cualquier persona con el enlace").
  *  - YouTube: usa la miniatura pública del video.
  *  - Imágenes: muestra la misma imagen.
  * Si no hay portada posible, devuelve null y se muestra un ícono según el tipo.
  */
 miniatura(m:MaterialSolucion):string|null{
  const url=this.enlace(m);
  if(!url)return null;
  const drive=this.idDrive(url);
  if(drive)return `https://drive.google.com/thumbnail?id=${drive}&sz=w640`;
  const youtube=this.idYoutube(url);
  if(youtube)return `https://img.youtube.com/vi/${youtube}/hqdefault.jpg`;
  return m.tipo==='IMAGEN'?url:null;
 }

 nombreTipo(tipo:MaterialSolucion['tipo']):string{
  return {VIDEO:'Video',PDF:'Documento PDF',IMAGEN:'Imagen',ENLACE:'Enlace'}[tipo]??tipo;
 }

 accion(tipo:MaterialSolucion['tipo']):string{
  return {VIDEO:'Ver video',PDF:'Abrir documento',IMAGEN:'Ver imagen',ENLACE:'Abrir enlace'}[tipo]??'Abrir recurso';
 }

 /** Saca el id de un archivo de Drive, venga como /file/d/ID/... o como ?id=ID. */
 private idDrive(url:string):string|null{
  try{
   const u=new URL(url);
   if(u.hostname!=='drive.google.com'&&u.hostname!=='docs.google.com')return null;
   const id=u.pathname.match(/\/d\/([\w-]{10,})/)?.[1]??u.searchParams.get('id');
   return id&&/^[\w-]+$/.test(id)?id:null;
  }catch{return null;}
 }

 /** Saca el id de un video de YouTube (youtu.be, watch?v=, embed o shorts). */
 private idYoutube(url:string):string|null{
  try{
   const u=new URL(url);
   const host=u.hostname.replace(/^www\.|^m\./,'');
   let id:string|null=null;
   if(host==='youtu.be')id=u.pathname.slice(1);
   else if(host==='youtube.com')id=u.searchParams.get('v')??u.pathname.match(/\/(?:embed|shorts)\/([\w-]+)/)?.[1]??null;
   return id&&/^[\w-]{6,}$/.test(id)?id:null;
  }catch{return null;}
 }

 ngOnDestroy(){this.cancelar.next();this.cancelar.complete();}
}
