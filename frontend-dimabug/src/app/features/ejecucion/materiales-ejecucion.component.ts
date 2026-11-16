import { Component, Input, OnChanges, OnDestroy, inject } from '@angular/core';
import { Subject, finalize, takeUntil } from 'rxjs';
import { ProcedimientoService } from '../../core/services/procedimiento.service';
import { MaterialPaso, TipoMaterialProcedimiento } from '../../core/models/procedimiento.model';
import { esUrlDescargaAutenticada } from '../../core/material/material-archivo';
import { plazoConsulta, mensajePlazo } from '../../core/http/plazo-consulta';

/** Consulta de solo lectura de los IDs históricos de la ejecución; nunca busca la revisión vigente. */
@Component({
  selector:'app-materiales-ejecucion',standalone:true,
  templateUrl:'./materiales-ejecucion.component.html',
  styleUrls:['../tickets/tickets-comun.css','./materiales-ejecucion.component.css'],
})
export class MaterialesEjecucionComponent implements OnChanges, OnDestroy {
  private readonly api=inject(ProcedimientoService);
  @Input({required:true}) procedimientoId!:number;
  @Input({required:true}) pasoId!:number;
  materiales:MaterialPaso[]=[];
  cargando=false;
  error='';
  errorDescarga='';
  descargando:number|null=null;
  private clave='';
  private readonly cancelar=new Subject<void>();
  private readonly destruir=new Subject<void>();
  ngOnChanges():void {
    const clave=`${this.procedimientoId}:${this.pasoId}`;
    if(clave===this.clave)return;
    this.clave=clave;this.cargar();
  }
  cargar():void {
    this.cancelar.next();this.materiales=[];this.error='';this.errorDescarga='';
    if(!Number.isInteger(this.procedimientoId)||this.procedimientoId<1||!Number.isInteger(this.pasoId)||this.pasoId<1)return;
    this.cargando=true;
    this.api.listarMateriales(this.procedimientoId,this.pasoId).pipe(plazoConsulta(),takeUntil(this.cancelar),finalize(()=>this.cargando=false)).subscribe({
      next:items=>this.materiales=items,
      error:(e:Error)=>this.error=mensajePlazo(e,'No fue posible cargar el material de apoyo. Intenta nuevamente.'),
    });
  }
  esLocal(material:MaterialPaso):boolean{return esUrlDescargaAutenticada(material.url);}
  enlace(material:MaterialPaso):string|null {
    if(this.esLocal(material))return null;
    try {const url=new URL(material.url);return ['https:','http:'].includes(url.protocol)&&!url.username&&!url.password?url.href:null;}catch{return null;}
  }
  etiqueta(tipo:TipoMaterialProcedimiento):string{return ({VIDEO:'Video',PDF:'PDF',IMAGEN:'Imagen',ENLACE:'Enlace'})[tipo]??'Recurso';}
  descargar(material:MaterialPaso):void {
    if(this.descargando!==null||!this.esLocal(material))return;
    this.descargando=material.id;this.errorDescarga='';
    this.api.descargarMaterial(material).pipe(takeUntil(this.destruir),finalize(()=>this.descargando=null)).subscribe({error:(e:Error)=>this.errorDescarga=e.message});
  }
  ngOnDestroy():void {this.cancelar.next();this.cancelar.complete();this.destruir.next();this.destruir.complete();}
}
