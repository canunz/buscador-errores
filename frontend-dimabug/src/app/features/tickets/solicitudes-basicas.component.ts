import { CatalogosSolicitud, DatosSolicitanteFormulario, contactoSolicitanteValido, equipoSolicitanteValido } from '../../core/models/datos-solicitante.model';
import { Component, inject, OnDestroy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription, finalize, last } from 'rxjs';
import { TicketService } from '../../core/services/ticket.service';
import { AuthService } from '../../core/services/auth.service';
import { TicketDetalle, TicketResumen } from '../../core/models/ticket.model';

/**
 * Pantalla de solicitudes básicas.
 * Carga los datos con su servicio y maneja lo que el usuario hace en la vista.
 */
@Component({
  standalone: true, imports: [FormsModule, DatePipe],
  templateUrl:'./solicitudes-basicas.component.html',
  styleUrls:['./tickets-comun.css','./solicitudes-basicas.component.css']
})
export class SolicitudesBasicasComponent implements OnDestroy {
  private readonly api=inject(TicketService);
  readonly auth=inject(AuthService);
  tickets:TicketResumen[]=[]; detalle:TicketDetalle|null=null;
  titulo=''; descripcion=''; pagina=0; hayMas=false;
  solicitante:DatosSolicitanteFormulario={nombreCompleto:'',sucursalId:null,departamentoId:null,contacto:'',equipo:'',codigoUsuario:''};
  catalogos:CatalogosSolicitud={sucursales:[],departamentos:[]};
  catalogosListos=false; errorCatalogos='';
  readonly contactoValido=contactoSolicitanteValido;
  readonly equipoValido=equipoSolicitanteValido;
  datosValidos():boolean {
    const d=this.solicitante;
    return this.catalogosListos && !!d.nombreCompleto.trim() && d.nombreCompleto.trim().length<=150
      && this.catalogos.sucursales.some(s=>s.id===d.sucursalId)
      && this.catalogos.departamentos.some(s=>s.id===d.departamentoId)
      && this.contactoValido(d.contacto) && this.equipoValido(d.equipo) && d.codigoUsuario.length<=80;
  }
  cargarCatalogos():void {
    this.errorCatalogos='';this.catalogosListos=false;
    this.subs.add(this.api.catalogosSolicitud().subscribe({next:c=>{
      this.catalogos=c;this.catalogosListos=c.sucursales.length>0&&c.departamentos.length>0;
      if(!this.catalogosListos)this.errorCatalogos='No hay sucursales o departamentos disponibles. Contacta a Soporte TI.';
    },error:()=>this.errorCatalogos='No pudimos cargar sucursales y departamentos. Intenta nuevamente.'}));
  }
  cargando=false; enviando=false; cargandoDetalle=false; limitesListos=false;
  error=''; errorLista=''; errorDetalle=''; exito='';
  maxFotos=5; maxBytes=5*1024*1024;
  fotos:{archivo:File;url:string}[]=[];
  imagenes:{id:number;nombre:string;url:string}[]=[];
  private detalleSub=new Subscription();
  private readonly subs=new Subscription();
  constructor() {
    this.cargar();
    this.cargarCatalogos();
    this.subs.add(this.api.limitesSolicitud().subscribe({next:l=>{
      this.maxFotos=l.cantidad;this.maxBytes=l.bytes;this.limitesListos=true;
    },error:()=>this.error='No pudimos comprobar los límites de fotografías. Actualiza la página para continuar.'}));
  }
  cargar():void {
    if(this.cargando) return;
    this.cargando=true;this.errorLista='';
    this.subs.add(this.api.listar({page:this.pagina,size:10}).pipe(finalize(()=>this.cargando=false)).subscribe({next:p=>{
      this.tickets=p.contenido;this.hayMas=this.pagina+1<p.totalPaginas;
    },error:()=>this.errorLista='No fue posible cargar tus solicitudes. Intenta actualizar.'}));
  }
  cambiarPagina(delta:number):void {if(this.cargando)return;this.pagina+=delta;this.cargar();}
  seleccionar(event:Event):void {
    if(this.enviando || !this.limitesListos)return;
    const input=event.target as HTMLInputElement;
    this.error='';
    const files=Array.from(input.files??[]); input.value='';
    if(this.fotos.length+files.length>this.maxFotos) {this.error=`Puedes adjuntar hasta ${this.maxFotos} fotografías.`;return;}
    if(files.some(f=>!['image/jpeg','image/png','image/webp'].includes(f.type)||f.size===0||f.size>this.maxBytes)) {
      this.error=`Utiliza JPG, PNG o WEBP de hasta ${this.maxBytes/1024/1024} MB por fotografía.`;return;
    }
    files.forEach(archivo=>this.fotos.push({archivo,url:URL.createObjectURL(archivo)}));
  }
  quitar(i:number):void {if(this.enviando)return;URL.revokeObjectURL(this.fotos[i].url);this.fotos.splice(i,1);}
  abrir(id:number):void {
    this.detalleSub.unsubscribe();this.detalleSub=new Subscription();this.limpiarImagenes();
    this.errorDetalle='';this.detalle=null;this.cargandoDetalle=true;
    this.detalleSub.add(this.api.obtener(id).pipe(last(),finalize(()=>this.cargandoDetalle=false)).subscribe({next:t=>{
      this.detalle=t;this.limpiarImagenes();
      for(const a of t.adjuntos??[]) this.detalleSub.add(this.api.archivo(t.id,a.id).subscribe({next:b=>{
        if(!['image/jpeg','image/png','image/webp'].includes(b.type)) {this.errorDetalle='Una fotografía no tiene un formato compatible.';return;}
        this.imagenes.push({id:a.id,nombre:a.nombre,url:URL.createObjectURL(b)});
      },error:()=>this.errorDetalle='No fue posible cargar una fotografía. Vuelve a abrir la solicitud para intentar nuevamente.'}));
    },error:()=>this.errorDetalle='No fue posible consultar esta solicitud.'}));
  }
  crear():void {
    if(this.enviando||!this.limitesListos||!this.datosValidos()||!this.titulo.trim()||!this.descripcion.trim())return;
    this.enviando=true;this.error='';this.exito='';
    this.subs.add(this.api.crearSolicitud(this.titulo.trim(),this.descripcion.trim(),this.fotos.map(f=>f.archivo),{...this.solicitante})
      .pipe(finalize(()=>this.enviando=false)).subscribe({next:t=>{
        this.titulo='';this.descripcion='';this.fotos.forEach(f=>URL.revokeObjectURL(f.url));this.fotos=[];
        this.exito=`Solicitud ${t.codigo} enviada. Puedes consultar su estado aquí.`;
        this.pagina=0;this.cargar();this.abrir(t.id);
      },error:(err:Error)=>this.error=/fotograf|formato|nombre|teléfono|anexo|sucursal|departamento|IP|AnyDesk|datos del solicitante|código de usuario/i.test(err.message) ? err.message : 'No pudimos confirmar el envío. Actualiza el historial antes de volver a enviar para evitar duplicados.'}));
  }
  estado(valor:string):string {return ({ABIERTO:'Abierto',EN_PROGRESO:'En progreso',EN_ESPERA:'En espera',RESUELTO:'Resuelto',CERRADO:'Cerrado',CANCELADO:'Cancelado'} as Record<string,string>)[valor]??valor;}
  private limpiarImagenes():void {this.imagenes.forEach(i=>URL.revokeObjectURL(i.url));this.imagenes=[];}
  ngOnDestroy():void {this.subs.unsubscribe();this.detalleSub.unsubscribe();this.limpiarImagenes();this.fotos.forEach(f=>URL.revokeObjectURL(f.url));}
}
