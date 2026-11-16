import { finalize, Subject, takeUntil } from 'rxjs';
import { plazoConsulta, mensajePlazo } from '../../core/http/plazo-consulta';
import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, HostListener, inject } from '@angular/core';
import { A11yModule } from '@angular/cdk/a11y';
import { DestinatariosEditorComponent } from '../../shared/ui/destinatarios-editor.component';
import { DestinatarioPaso } from '../../core/models/configuracion-paso.model';
import { SolicitudEvidencia } from '../../core/services/automatizacion-ejecucion.service';
import { FormsModule } from '@angular/forms';
import { EjecucionPaso } from '../../core/models/ejecucion.model';
import { configuracionInicial } from '../../core/models/configuracion-paso.model';
import { ActividadEvidencia, AutomatizacionEjecucionService, SolicitudConFotos } from '../../core/services/automatizacion-ejecucion.service';
@Component({
  selector: 'app-validacion-externa', standalone: true, imports: [FormsModule, A11yModule, DestinatariosEditorComponent],
  templateUrl: './validacion-externa.component.html', styleUrls: ['../tickets/tickets-comun.css', './validacion-externa.component.css'],
})
export class ValidacionExternaComponent implements OnChanges, OnDestroy {
  private readonly api = inject(AutomatizacionEjecucionService);
  @Input({ required: true }) ejecucionId!: number;
  @Input({ required: true }) paso!: EjecucionPaso;
  @Input() enCurso = false;
  @Input() puedeEnviar = true;
  @Output() actualizada = new EventEmitter<{ avanzar: boolean; pasoId: number }>();
  @Output() ocupacion = new EventEmitter<boolean>();
  modalAbierto=false;
  destinatarios:DestinatarioPaso[]=[];asunto='';mensaje='';errorModal='';resultadoEnvio:SolicitudEvidencia|null=null;
  disponible=false;modoSimulado=false;
  get tienePrincipal(){return this.destinatarios.some(d=>d.tipo==='PRINCIPAL');}
  get esCorreo(){return this.esEvidencia || this.paso?.modoEjecucion==='ENVIAR_CORREO';}
  abrirCorreo():void {
    if(!this.enCurso || !this.esCorreo || this.ocupado || this.solicitud || (this.cargando && !this.consultaRealizada))return;
    this.modalAbierto=true;this.errorModal='';
    this.destinatarios=this.configuracion.destinatarios.map(d=>({...d}));
    this.asunto=this.configuracion.asunto??'';
    this.mensaje=this.configuracion.mensaje?.trim()||this.paso.instruccion;
  }
  cerrarCorreo():void{if(!this.ocupado){this.modalAbierto=false;this.errorModal='';}}
  @HostListener('document:keydown.escape') escape():void {if(this.modalAbierto)this.cerrarCorreo();}
  enviarCorreo():void {
    if(this.ocupado||!this.modalAbierto||!this.puedeEnviar||!this.enCurso||!this.disponible||!this.destinatarios.some(d=>d.tipo==='PRINCIPAL')||!this.asunto.trim()||!this.mensaje.trim())return;
    this.ocupado=true;this.errorModal='';
    this.api.enviar(this.ejecucionId,this.paso.ejecucionPasoId,{destinatarios:this.destinatarios,asunto:this.asunto,mensaje:this.mensaje}).subscribe({
      next:s=>{this.ocupado=false;this.resultadoEnvio=s;this.modalAbierto=false;
        this.aviso=s.mensajeEnviadoId?.startsWith('SIMULADO-')?'Correo simulado correctamente. No se envió un mensaje real':s.mensajeEnviadoId?'El servicio de correo aceptó el mensaje. La entrega en la bandeja de entrada aún no está confirmada.':'La solicitud sigue pendiente de envío';
        if(['ERROR','ENVIANDO'].includes(s.estado)){this.aviso='';this.error='No se confirmó el envío del correo. Revisa el historial antes de intentar un nuevo envío; la solicitud quedó registrada y no se reenviará automáticamente.';}
        this.actualizada.emit({avanzar:s.estado==='ENVIADA',pasoId:this.paso.ejecucionPasoId});this.cargar();},
      error:(e:Error)=>{this.ocupado=false;this.errorModal=e.message;}
    });
  }
  fechaLocal(fecha:string|null):string {return fecha?new Intl.DateTimeFormat('es-CL',{dateStyle:'short',timeStyle:'medium',timeZone:'America/Santiago'}).format(new Date(fecha)):'Pendiente';}
  detalleEvento(evento:ActividadEvidencia):{descripcion:string;estado:string;responsable:string;destinatarios:DestinatarioPaso[];evidencias:number[]} {
    try {const d=JSON.parse(evento.detalle);if(d && typeof d.descripcion==='string')return d;}catch{}
    return {descripcion:evento.detalle,estado:'Registro histórico',responsable:'Sistema / usuario registrado',destinatarios:[],evidencias:[]};
  }
  solicitud: SolicitudConFotos | null = null;
  actividad: ActividadEvidencia[] = [];
  fotos: Record<number, string> = {};
  archivos: Record<string, File> = {};
  previsualizaciones: Record<string,string> = {};
  remitente = '';
  motivo = '';
  definitiva = false;
  confirmacionTexto = '';
  get configuracion(){return this.solicitud?.detalle.politica.configuracion ?? configuracionInicial(this.paso?.modoEjecucion ?? 'SOLICITAR_EVIDENCIA',!!this.paso?.esCritico,this.paso?.configuracion);}
  get esEvidencia(){return ['SOLICITAR_EVIDENCIA','VALIDACION_EXTERNA'].includes(this.paso?.modoEjecucion ?? '');}
  get revisable(){return ['EVIDENCIA_RECIBIDA','CONFIRMACION_RECIBIDA','ENVIADA_PENDIENTE_APROBACION','ACCION_PENDIENTE_APROBACION'].includes(this.solicitud?.detalle.estado ?? '');}
  get categorias(){return this.configuracion.evidenciasRequeridas;}
  errorConsulta = '';
  private readonly cancelarConsulta = new Subject<void>();
  get estadoSolicitud(): string {
    if(this.errorConsulta)return 'ERROR';
    if(this.cargando && !this.consultaRealizada)return 'CARGANDO';
    if(!this.solicitud)return 'PENDIENTE_SOLICITUD';
    const estado=this.solicitud.detalle.estado;
    return ['ENVIADA','ENVIADA_PENDIENTE_APROBACION'].includes(estado)?'CORREO_ENVIADO':estado;
  }
  error = '';
  aviso = '';
  private busy = false;
  get ocupado(): boolean { return this.busy; }
  set ocupado(value: boolean) { this.busy = value; this.ocupacion.emit(value); }
  cargando = false;
  consultaRealizada=false;
  private ejecucionCargada:number|null=null;
  private revisionPaso='';
  private refrescoPendiente=false;
  private mensajeId = '';
  private timer: ReturnType<typeof setInterval> | null = null;
  private destruido = false;
  private pendientes = new Set<number>();
  private pasoCargado: number | null = null;
  private cargadas = new Set<number>();
  fotoCargada(id: number): void { this.cargadas.add(id); }
  get fotosListas(): boolean { return !!this.solicitud && this.solicitud.fotos.length === this.categorias.length && this.solicitud.fotos.every((f) => !!this.fotos[f.id] && this.cargadas.has(f.id)); }
  ngOnChanges(): void {
    const cambiaContexto=this.pasoCargado!==this.paso?.ejecucionPasoId || this.ejecucionCargada!==this.ejecucionId;
    const revision=`${this.paso?.version??0}|${this.paso?.estado??''}|${this.enCurso}`;
    const actualizar=cambiaContexto || revision!==this.revisionPaso;
    if(cambiaContexto){
      this.refrescoPendiente=false;this.cancelarConsulta.next();this.errorConsulta='';this.consultaRealizada=false;
      this.pasoCargado=this.paso?.ejecucionPasoId??null;this.ejecucionCargada=this.ejecucionId;
      this.solicitud=null;this.actividad=[];this.remitente='';this.motivo='';this.error='';this.aviso='';
      Object.keys(this.previsualizaciones).forEach(c=>this.quitarArchivo(c));this.modalAbierto=false;this.resultadoEnvio=null;this.mensajeId='';this.archivos={};
      for(const url of Object.values(this.fotos))URL.revokeObjectURL(url);
      this.fotos={};this.cargadas.clear();this.pendientes.clear();
    }
    this.revisionPaso=revision;
    if(actualizar){if(this.cargando)this.refrescoPendiente=true;else this.cargar();}
    if(this.timer===null)this.timer=setInterval(()=>{
      const espera=this.solicitud && ['ESPERANDO_EVIDENCIA','ESPERANDO_CONFIRMACION','EVIDENCIA_RECIBIDA','CONFIRMACION_RECIBIDA','ENVIADA_PENDIENTE_APROBACION','ACCION_PENDIENTE_APROBACION','PENDIENTE'].includes(this.solicitud.detalle.estado);
      if(espera && document.visibilityState==='visible' && this.enCurso && !this.ocupado && !this.modalAbierto && !this.errorConsulta)this.cargar();
    },5000);
  }
  ngOnDestroy(): void { Object.keys(this.previsualizaciones).forEach(c=>this.quitarArchivo(c)); this.destruido = true; this.cancelarConsulta.next();this.cancelarConsulta.complete(); this.ocupacion.emit(false); if (this.timer !== null) clearInterval(this.timer); for (const url of Object.values(this.fotos)) URL.revokeObjectURL(url); }
  cargar(): void {
    if (!this.ejecucionId || !this.paso || this.cargando || this.ocupado || this.destruido) return;
    const pasoId = this.paso.ejecucionPasoId;
    this.cargando = true;this.errorConsulta='';
    this.api.estado(this.ejecucionId).pipe(plazoConsulta(),takeUntil(this.cancelarConsulta),finalize(()=>{this.cargando=false;if(this.refrescoPendiente && !this.destruido){this.refrescoPendiente=false;this.cargar();}})).subscribe({
      next: (estado) => {
        this.cargando = false;this.consultaRealizada=true;this.errorConsulta='';
        if (this.destruido) return;
        if (this.paso.ejecucionPasoId !== pasoId) { this.cargar(); return; }
        this.disponible=estado.disponible;this.modoSimulado=estado.simulado;
        const anterior = this.solicitud;
        this.solicitud = estado.solicitudes.filter((s) => s.detalle.ejecucionPasoId === pasoId).sort((a, b) => b.detalle.ronda - a.detalle.ronda)[0] ?? null;
        const rondas=estado.solicitudes.filter(s=>s.detalle.ejecucionPasoId===pasoId).map(s=>s.detalle.id);
        this.actividad = estado.comunicaciones?estado.comunicaciones.filter(c=>c.pasoId===pasoId).map(c=>({...c.evento,detalle:JSON.stringify({descripcion:c.evento.detalle,estado:c.estado,responsable:c.responsable,destinatarios:c.destinatarios,evidencias:c.evidencias})})):estado.actividad.filter(a=>rondas.includes(a.solicitudId)&&!['CONTEXTO_INICIO','CONFIGURACION_CORREO'].includes(a.tipo));
        if (this.solicitud && anterior?.detalle.id !== this.solicitud.detalle.id) {
          Object.keys(this.previsualizaciones).forEach(c=>this.quitarArchivo(c));this.remitente = this.solicitud.detalle.contexto.responsableEmail; this.mensajeId = ''; this.archivos = {}; this.motivo = ''; this.definitiva = false;
        }
        if (anterior && anterior.detalle.estado !== 'EVIDENCIA_RECIBIDA' && this.solicitud?.detalle.estado === 'EVIDENCIA_RECIBIDA') this.aviso = 'Llegaron fotografías. Revisa las evidencias solicitadas antes de decidir.';
        for (const foto of this.solicitud?.fotos ?? []) if (!this.fotos[foto.id] && !this.pendientes.has(foto.id)) {
          this.pendientes.add(foto.id);
          this.api.foto(this.ejecucionId, this.solicitud!.detalle.id, foto.id).pipe(takeUntil(this.cancelarConsulta)).subscribe({
            next: (blob) => { this.pendientes.delete(foto.id); if (!this.destruido && this.paso.ejecucionPasoId === pasoId) this.fotos[foto.id] = URL.createObjectURL(blob); },
            error: (e: Error) => { this.pendientes.delete(foto.id); this.error = e.message; },
          });
        }
      },
      error: (e: Error) => { this.errorConsulta = mensajePlazo(e,'No fue posible consultar la solicitud. Intenta nuevamente.'); },
    });
  }
  cargarFotoHistorial(solicitudId:string,fotoId:number):void {
    if(this.fotos[fotoId]||this.pendientes.has(fotoId))return;
    this.pendientes.add(fotoId);this.api.foto(this.ejecucionId,solicitudId,fotoId).pipe(takeUntil(this.cancelarConsulta)).subscribe({next:blob=>{this.pendientes.delete(fotoId);if(!this.destruido)this.fotos[fotoId]=URL.createObjectURL(blob);},error:(e:Error)=>{this.pendientes.delete(fotoId);this.error=e.message;}});
  }
  quitarArchivo(categoria:string):void {
    if(this.previsualizaciones[categoria])URL.revokeObjectURL(this.previsualizaciones[categoria]);
    delete this.previsualizaciones[categoria];delete this.archivos[categoria];
  }
  seleccionar(categoria: string, event: Event): void {
    const input=event.target as HTMLInputElement;
    const file=input.files?.[0];this.quitarArchivo(categoria);this.error='';
    if(!file)return;
    if(!['image/jpeg','image/png'].includes(file.type)||file.size>2*1024*1024||!file.size){this.error='Usa una imagen JPEG o PNG de hasta 2 MB.';input.value='';return;}
    this.archivos[categoria]=file;this.previsualizaciones[categoria]=URL.createObjectURL(file);
    input.value='';
  }
  get tresArchivos(): boolean { return this.categorias.every((key) => !!this.archivos[key.toLowerCase()]); }
  recibir(muestras: boolean): void {
    if (!this.solicitud || this.ocupado || !this.enCurso || (!muestras && !this.tresArchivos)) return;
    this.ocupado = true; this.error = ''; this.aviso = '';
    this.mensajeId ||= crypto.randomUUID();
    const peticion = muestras ? this.api.simular(this.ejecucionId, this.solicitud.detalle.id, this.remitente, this.mensajeId) : this.api.subir(this.ejecucionId, this.solicitud.detalle.id, this.remitente, this.mensajeId, this.archivos);
    peticion.subscribe({
      next: (s) => { this.ocupado = false; this.actualizada.emit({ avanzar: s.estado==='APROBADA', pasoId: this.paso.ejecucionPasoId }); this.cargar(); },
      error: (e: Error) => { this.ocupado = false; this.error = e.message; this.cargar(); },
    });
  }
  decidir(aprobar: boolean): void {
    if (!this.solicitud || !this.revisable || this.ocupado || !this.enCurso || (aprobar && this.esEvidencia && !this.fotosListas) || (!aprobar && !this.motivo.trim())) return;
    this.ocupado = true; this.error = ''; this.aviso = '';
    this.api.decidir(this.ejecucionId, this.solicitud.detalle, aprobar, this.motivo, this.definitiva, this.esEvidencia).subscribe({
      next: () => { this.ocupado = false; this.aviso = aprobar ? 'Fotografías aprobadas. El paso quedó completado.' : this.definitiva ? 'Rechazo definitivo registrado.' : 'Fotografías rechazadas. Puedes solicitar nuevas evidencias; las anteriores se conservan.'; this.actualizada.emit({ avanzar: aprobar, pasoId: this.paso.ejecucionPasoId }); this.cargar(); },
      error: (e: Error) => { this.ocupado = false; this.error = e.message; this.cargar(); },
    });
  }
  solicitarNuevamente():void {
    if(!this.enCurso || this.ocupado || !this.solicitud || this.solicitud.detalle.estado!=='RECHAZADA')return;
    this.ocupado=true;this.error='';
    this.api.solicitarNuevamente(this.ejecucionId,this.solicitud.detalle).pipe(finalize(()=>{this.ocupado=false;this.cargar();})).subscribe({
      next:()=>{this.aviso='Nueva solicitud registrada. Revisa su estado de envío en el historial.';this.actualizada.emit({avanzar:false,pasoId:this.paso.ejecucionPasoId});},
      error:(e:Error)=>{this.error=e.message;}
    });
  }
  simularTiempo(horas: number): void {
    if (!this.solicitud || this.ocupado || !this.enCurso) return;
    this.ocupado = true; this.error = '';
    this.api.tiempo(this.ejecucionId, this.solicitud.detalle.id, horas).subscribe({
      next: () => { this.ocupado = false; this.cargar(); },
      error: (e: Error) => { this.ocupado = false; this.error = e.message; },
    });
  }
  confirmarRespuesta():void {
    if(!this.solicitud || this.ocupado || !this.enCurso || !this.confirmacionTexto.trim())return;
    this.ocupado=true;this.error='';this.mensajeId ||= crypto.randomUUID();
    this.api.confirmar(this.ejecucionId,this.solicitud.detalle.id,this.remitente,this.mensajeId,this.confirmacionTexto).subscribe({
      next:s=>{this.ocupado=false;this.actualizada.emit({avanzar:s.estado==='APROBADA',pasoId:this.paso.ejecucionPasoId});this.cargar();},
      error:(e:Error)=>{this.ocupado=false;this.error=e.message;this.cargar();}
    });
  }
  nombreCategoria(id:string):string{return ({MUEBLE:'Mueble',RED:'Punto de red',ELECTRICIDAD:'Conexión eléctrica',FOTO_GENERAL:'Fotografía general'} as Record<string,string>)[id]??id;}
  tipoEvento(tipo:string):string{return ({SOLICITUD_CREADA:'Solicitud preparada',SOLICITUD_ENVIADA_SIMULADA:'Correo simulado',RECORDATORIO_SIMULADO:'Recordatorio simulado',EVIDENCIA_RECIBIDA:'Respuesta con fotografías',EVIDENCIA_APROBADA:'Resultado aprobado',EVIDENCIA_RECHAZADA:'Resultado rechazado',NUEVA_RONDA:'Nueva solicitud',CONFIRMACION_RECIBIDA:'Confirmación recibida',ESCALADO_AL_TECNICO:'Plazo vencido: aviso al técnico',SOLICITUD_CANCELADA:'Comunicación cancelada'} as Record<string,string>)[tipo]??tipo.replaceAll('_',' ').toLocaleLowerCase('es');}
  etiqueta(estado: string): string {
    return ({ PENDIENTE: 'Esperando los pasos anteriores', ESPERANDO_EVIDENCIA: 'Esperando fotografías', EVIDENCIA_RECIBIDA: 'Evidencias disponibles para revisión', APROBADA: 'Resultado validado', RECHAZADA: 'Resultado rechazado', RECHAZADA_DEFINITIVA: 'Rechazo definitivo', CANCELADA: 'Solicitud cancelada', ERROR: 'Envío no confirmado: revisar historial',ENVIANDO:'Confirmando envío del correo; no reenviar',ENVIADA:'Solicitud enviada (no acredita carga de folios)',ESPERANDO_CONFIRMACION:'Esperando respuesta de los responsables',CONFIRMACION_RECIBIDA:'Confirmación recibida: requiere validación',ENVIADA_PENDIENTE_APROBACION:'Envío registrado: requiere aprobación',ACCION_PENDIENTE_APROBACION:'Acción automática exitosa: requiere aprobación' } as Record<string, string>)[estado] ?? estado;
  }
}
