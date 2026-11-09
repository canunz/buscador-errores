import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfiguracionPaso, ModalidadPaso, configuracionInicial, modalidadVisible } from '../../core/models/configuracion-paso.model';
import { DestinatariosEditorComponent } from './destinatarios-editor.component';
@Component({selector:'app-configuracion-paso',standalone:true,imports:[FormsModule,DestinatariosEditorComponent],templateUrl:'./configuracion-paso.component.html',styleUrl:'./configuracion-paso.component.css'})
export class ConfiguracionPasoComponent implements OnChanges {
  @Input() modo:ModalidadPaso='MANUAL_GUIADO';@Output() modoChange=new EventEmitter<ModalidadPaso>();
  @Input() value:ConfiguracionPaso|null=null;@Output() valueChange=new EventEmitter<ConfiguracionPaso>();
  @Input() critico=false;@Input() disabled=false;
  modalidad:ModalidadPaso='MANUAL_GUIADO';config=configuracionInicial('MANUAL_GUIADO',false);reglas='24,72';
  readonly acciones: {modo:ModalidadPaso;nombre:string}[] = [
    {modo:'MANUAL_GUIADO',nombre:'Seguir instrucciones'},
    {modo:'SOLICITAR_EVIDENCIA',nombre:'Solicitar evidencias'},
    {modo:'ENVIAR_CORREO',nombre:'Enviar correo'}
  ];
  private borradores = new Map<ModalidadPaso,{modo:ModalidadPaso;config:ConfiguracionPaso}>();
  get accionSeleccionada():ModalidadPaso {return this.modalidad==='INFORMATIVO'?'MANUAL_GUIADO':this.modalidad;}
  opcionesEvidencia=[{id:'MUEBLE',nombre:'Mueble'},{id:'RED',nombre:'Punto de red'},{id:'ELECTRICIDAD',nombre:'Conexión eléctrica'},{id:'FOTO_GENERAL',nombre:'Fotografía general'}];
  get esCorreo(){return ['SOLICITAR_EVIDENCIA','ENVIAR_CORREO'].includes(this.modalidad);}
  alternarEvidencia(id:string){this.config.evidenciasRequeridas=this.config.evidenciasRequeridas.includes(id)?this.config.evidenciasRequeridas.filter(e=>e!==id):[...this.config.evidenciasRequeridas,id];this.emitir();}
  get esAccion(){return ['SOLICITAR_EVIDENCIA','ENVIAR_CORREO','AUTOMATICO'].includes(this.modalidad);}
  ngOnChanges(){this.modalidad=modalidadVisible(this.modo);this.config=configuracionInicial(this.modalidad,this.critico,this.value);this.reglas=this.config.recordatoriosHoras.join(',');}
  cambiarModalidad(modo:ModalidadPaso){
    if(this.disabled || modo===this.accionSeleccionada)return;
    this.borradores.set(this.accionSeleccionada,{modo:this.modo,config:structuredClone(this.config)});
    const anterior=this.borradores.get(modo);
    const destino=anterior?.modo??modo;
    this.modalidad=modalidadVisible(destino);
    this.config=configuracionInicial(this.modalidad,this.critico,anterior?.config??{destinatarios:[...this.config.destinatarios],usarDestinatariosSucursal:this.config.usarDestinatariosSucursal,mensaje:this.config.mensaje,asunto:this.config.asunto});
    this.reglas=this.config.recordatoriosHoras.join(',');this.modo=destino;
    this.modoChange.emit(destino);this.emitir();
  }
  cambiarReglas(texto:string){this.reglas=texto;this.config.recordatoriosHoras=texto.trim()?texto.split(',').map(v=>Number(v.trim())):[];this.emitir();}
  ajustarCondicion(){if(this.config.condicionFinalizacion==='EVIDENCIAS_APROBADAS'||this.config.condicionFinalizacion==='RESPUESTA_VALIDADA')this.config.requiereAprobacion=true;this.emitir();}
  emitir(){this.valueChange.emit({...this.config,destinatarios:[...this.config.destinatarios],evidenciasRequeridas:[...this.config.evidenciasRequeridas],recordatoriosHoras:[...this.config.recordatoriosHoras]});}
}
