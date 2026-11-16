import {Component,Input,OnChanges,OnDestroy,inject} from '@angular/core';
import {FormsModule} from '@angular/forms';import {Clipboard} from '@angular/cdk/clipboard';
import {Subject,takeUntil,finalize} from 'rxjs';
import {AutoservicioService} from '../../core/services/autoservicio.service';
import {environment} from '../../../environments/environment';

/** URL pública, sin tokens, compartible tanto en NDEF URI como en QR. */
export function basePortalNfc(value:string):string|null {
 try {const u=new URL(value.trim());const h=u.hostname.toLowerCase();
  if(!['http:','https:'].includes(u.protocol)||u.username||u.password||value.includes('?')||value.includes('#')||h==='localhost'||h.endsWith('.localhost')||h==='0.0.0.0'||h.startsWith('127.')||h==='[::1]'||h==='[::]'||h==='0')return null;
  return u.href.replace(/\/+$/,'');
 }catch{return null;}
}
export function urlSolucionNfc(base:string,id:number):string|null {const valid=basePortalNfc(base);return valid&&Number.isSafeInteger(id)&&id>0?`${valid}/autoservicio/soluciones/${id}`:null;}
@Component({selector:'app-enlace-nfc',standalone:true,imports:[FormsModule],template:`<section aria-label="Enlace público NFC y QR"><h3>Enlace NFC y QR</h3>
@if(cargando){<p role="status">Comprobando publicación…</p>}
@if(error){<p role="alert">{{error}}</p>}
@if(publicada){
 @if(url;as enlace){<label>URL pública de esta solución<input type="text" [value]="enlace" readonly aria-label="URL pública para NFC y QR" /></label><button type="button" (click)="copiar()">Copiar enlace</button>@if(base){<p>URL lista para utilizar en NFC o QR cuando el portal esté publicado.</p>}@else{<p>Ruta relativa para pruebas. La URL completa se generará al configurar la dirección del despliegue. No programes etiquetas físicas todavía.</p>}}
 @else{<p role="status">Configura una dirección accesible desde los teléfonos. No se generan enlaces NFC hacia localhost.</p>}
 <details [open]="!base"><summary>Dirección del portal para las etiquetas</summary><label>Dominio o IP accesible desde los teléfonos<input type="url" [(ngModel)]="direccion" placeholder="https://portal.empresa.cl" [ngModelOptions]="{standalone:true}" /></label><button type="button" (click)="guardarBase()">Guardar dirección</button></details>
}
@if(aviso){<p role="status" aria-live="polite">{{aviso}}</p>}
</section>`,styles:[`section{margin:20px 0;padding:16px;border:1px solid #dbe4f5;border-radius:12px;background:#f6f8fc}label{display:block;font-weight:600;margin:12px 0}input{box-sizing:border-box;display:block;width:100%;min-width:0;padding:10px;margin-top:6px;border:1px solid #acbfdf;border-radius:8px;font:inherit}button{background:#2258d5;color:white;padding:10px 16px;border:0;border-radius:8px;font:inherit;cursor:pointer}button:focus-visible,input:focus-visible,summary:focus-visible{outline:3px solid #7caaff;outline-offset:3px}p{font-size:.9rem;line-height:1.5}details{margin-top:16px}summary{cursor:pointer}`]})
export class EnlaceNfcComponent implements OnChanges,OnDestroy {
 @Input({required:true}) solucionId!:number;
 private readonly api=inject(AutoservicioService);private readonly clipboard=inject(Clipboard);
 private readonly cancelar=new Subject<void>();
 direccion='';base='';publicada=false;cargando=false;error='';aviso='';
 constructor(){let saved='';try{saved=localStorage.getItem('dimabug.portal.publicBase')??'';}catch{}
  this.direccion=environment.publicPortalUrl||saved||globalThis.location.origin;this.base=basePortalNfc(this.direccion)??'';this.direccion=this.base;
 }
 get url(){if(!this.publicada||!Number.isSafeInteger(this.solucionId)||this.solucionId<1)return null;return urlSolucionNfc(this.base,this.solucionId)??`/autoservicio/soluciones/${this.solucionId}`;}
 ngOnChanges(){this.cancelar.next();this.publicada=false;this.error='';this.aviso='';this.cargando=true;
  if(!Number.isSafeInteger(this.solucionId)||this.solucionId<1){this.cargando=false;this.error='La solución no está disponible.';return;}
  this.api.detalle(this.solucionId).pipe(takeUntil(this.cancelar),finalize(()=>this.cargando=false)).subscribe({next:()=>this.publicada=true,error:()=>this.error='Esta solución no está disponible públicamente. Revisa su estado, autoservicio y publicación del conocimiento.'});
 }
 guardarBase(){this.aviso='';const base=basePortalNfc(this.direccion);if(!base){this.aviso='Usa una dirección HTTP/HTTPS válida, sin credenciales, parámetros ni localhost.';return;}
  this.base=base;this.direccion=base;try{localStorage.setItem('dimabug.portal.publicBase',base);}catch{}this.aviso='Dirección guardada en este navegador. Comprueba desde el teléfono que el portal sea accesible antes de programar la etiqueta.';
 }
 copiar(){const url=this.url;if(!url)return;this.aviso=this.clipboard.copy(url)?(this.base?'Enlace copiado.':'Ruta de prueba copiada. Falta configurar la base del despliegue para usarla en etiquetas.'):'No fue posible copiar. Selecciona la URL y cópiala manualmente.';}
 ngOnDestroy(){this.cancelar.next();this.cancelar.complete();}
}
