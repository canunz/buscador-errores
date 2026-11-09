import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DestinatarioPaso } from '../../core/models/configuracion-paso.model';
@Component({selector:'app-destinatarios-editor',standalone:true,imports:[FormsModule],template:`
  <fieldset [disabled]="disabled"><legend>{{ titulo }}</legend>
    <div class="fila"><label>Correo<input type="email" [(ngModel)]="correo" [ngModelOptions]="{standalone:true}" placeholder="Correo corporativo" /></label>
    <label>Tipo<select [(ngModel)]="tipo" [ngModelOptions]="{standalone:true}"><option value="PRINCIPAL">Para</option><option value="COPIA">CC</option></select></label>
    <button type="button" (click)="agregar()">Agregar nuevo correo</button></div>
    @if(error){<p role="alert">{{ error }}</p>}
    <ul>@for(d of value;track d.correo){<li>{{ d.correo }} · <label>Tipo<select [ngModel]="d.tipo" (ngModelChange)="cambiarTipo(d.correo,$event)" [ngModelOptions]="{standalone:true}" [attr.aria-label]="'Tipo de destinatario '+d.correo"><option value="PRINCIPAL">Para</option><option value="COPIA">CC</option></select></label> <button type="button" (click)="quitar(d.correo)" [attr.aria-label]="'Quitar destinatario '+d.correo">Quitar</button></li>}</ul>
  </fieldset>`,styles:[` :host{display:block} :focus-visible{outline:3px solid #2563eb;outline-offset:3px} button{border:1px solid #b7c8e2;border-radius:.55rem;background:#edf4ff;color:#163d7c;min-height:44px;font-weight:600} input,select{border:1px solid #a9bad3;border-radius:.5rem;min-height:44px;background:#fff;color:#12213c} ul{padding:0;list-style:none} li{display:flex;gap:.6rem;align-items:center;flex-wrap:wrap;background:#f4f7fc;padding:.65rem;border-radius:.6rem} li label{display:flex;align-items:center} fieldset{border:1px solid #dbe4f0;border-radius:.6rem;padding:1rem;margin:.75rem 0}.fila{display:flex;gap:.6rem;flex-wrap:wrap;align-items:end}label{display:grid;gap:.25rem}input,select{padding:.5rem}li{margin:.4rem 0;overflow-wrap:anywhere}button{padding:.4rem .6rem}p{color:#a51c30}`]})
export class DestinatariosEditorComponent {
  @Input() value: DestinatarioPaso[]=[];@Output() valueChange=new EventEmitter<DestinatarioPaso[]>();
  @Input() autorizados: string[] | null=null;@Input() disabled=false;@Input() titulo='Destinatarios';
  correo='';tipo:DestinatarioPaso['tipo']='PRINCIPAL';error='';
  agregar():void {
    if(this.disabled)return;const correo=this.correo.trim().toLowerCase();
    const local=correo.split('@')[0];
    if(correo.length>120 || local.length>64 || local.startsWith('.') || local.endsWith('.') || local.includes('..') || !/^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@dimarsa\.cl$/.test(correo)){this.error='Ingresa un único correo válido @dimarsa.cl.';return;}
    if(this.autorizados && !this.autorizados.includes(correo)){this.error='Selecciona un correo autorizado del catálogo o solicita su registro a un administrador.';return;}
    if(this.value.some(d=>d.correo.toLowerCase()===correo)){this.error='El destinatario ya está agregado.';return;}
    if(this.value.length>=20){this.error='Máximo 20 destinatarios.';return;}
    this.valueChange.emit([...this.value,{correo,tipo:this.tipo}]);this.correo='';this.error='';
  }
  cambiarTipo(correo:string,tipo:DestinatarioPaso['tipo']):void{if(!this.disabled)this.valueChange.emit(this.value.map(d=>d.correo===correo?{...d,tipo}:d));}
  quitar(correo:string):void{if(!this.disabled)this.valueChange.emit(this.value.filter(d=>d.correo!==correo));}
}
