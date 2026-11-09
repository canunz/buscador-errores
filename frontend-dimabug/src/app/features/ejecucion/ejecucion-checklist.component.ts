import { finalize, Subject, takeUntil } from 'rxjs';
import { DatePipe } from '@angular/common';
import { Component, OnInit, OnDestroy, HostListener, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Ejecucion, EjecucionPaso, ProcedimientoResumen } from '../../core/models/ejecucion.model';
import { EjecucionService } from '../../core/services/ejecucion.service';
import { ProcedimientoService } from '../../core/services/procedimiento.service';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';
import { ValidacionExternaComponent } from './validacion-externa.component';
import { MaterialesEjecucionComponent } from './materiales-ejecucion.component';

@Component({
  selector: 'app-ejecucion-checklist',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink, LoadingModalComponent, ValidacionExternaComponent, MaterialesEjecucionComponent],
  templateUrl: './ejecucion-checklist.component.html',
  styleUrls: ['../tickets/tickets-comun.css', './ejecucion-checklist.component.css'],
})
export class EjecucionChecklistComponent implements OnInit, OnDestroy {
  private readonly ejecuciones = inject(EjecucionService);
  private readonly procedimientos = inject(ProcedimientoService);
  private readonly route = inject(ActivatedRoute);

  ejecucion: Ejecucion | null = null;
  procedimiento: ProcedimientoResumen | null = null;
  pasos: EjecucionPaso[] = [];
  borradores: Record<number, string> = {};
  loading = false;
  cargandoPasos = false;
  errorPasos = '';
  pasosConsultados = false;
  error = '';
  guardando: number | null = null;
  gestionandoEvidencia = false;
  completando = false;
  cancelando = false;
  confirmarCancelacion = false;
  motivoCancelacion = '';
  pasoActual = 1;
  observacionListaId: number | null = null;
  private readonly versiones = new Map<number, number>();
  private readonly autosaves = new Map<number, ReturnType<typeof setTimeout>>();
  private actualizacion: ReturnType<typeof setInterval> | null = null;
  sincronizando = false;
  ultimaSincronizacion: Date | null = null;
  estadoSincronizacion = '';
  private destruido = false;
  private readonly cancelarConsultas = new Subject<void>();

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      this.error = 'La ejecución solicitada no está disponible.';
      this.loading = false;
      return;
    }
    this.cargar(id);
    this.actualizacion = setInterval(() => {
      if (document.visibilityState === 'visible' && this.enCurso && !this.cargandoPasos && this.guardando === null && !this.gestionandoEvidencia && !this.completando && !this.cancelando) {
        this.recargarPasos();
      }
    }, 20_000);
  }

  ngOnDestroy(): void {
    this.destruido = true;this.cancelarConsultas.next();this.cancelarConsultas.complete();
    if (this.actualizacion !== null) clearInterval(this.actualizacion);
    for (const timer of this.autosaves.values()) clearTimeout(timer);
    this.autosaves.clear();
  }

  tieneCambiosPendientes(): boolean { return this.enCurso && this.hayBorradoresPendientes; }
  operacionEnCurso(): boolean { return this.guardando !== null || this.gestionandoEvidencia || this.completando || this.cancelando; }

  @HostListener('window:beforeunload', ['$event'])
  antesDeSalir(event: BeforeUnloadEvent): void {
    if (this.tieneCambiosPendientes() || this.operacionEnCurso()) event.preventDefault();
  }

  get enCurso(): boolean {
    return this.ejecucion?.estado === 'EN_CURSO';
  }

  get cumplidos(): number {
    return this.pasos.filter((paso) => paso.cumplido).length;
  }

  get criticos(): number {
    return this.pasos.filter((paso) => paso.esCritico).length;
  }

  get porcentaje(): number {
    if (!this.pasos.length) {
      return 0;
    }
    return Math.round((this.cumplidos * 100) / this.pasos.length);
  }

  readonly circunferencia = 2 * Math.PI * 42;

  get anilloOffset(): number {
    return this.circunferencia * (1 - this.porcentaje / 100);
  }

  get paso(): EjecucionPaso | null {
    return this.pasos[this.pasoActual - 1] ?? null;
  }

  irPaso(numero: number): void {
    if (this.gestionandoEvidencia || numero < 1 || numero > this.pasos.length) {
      return;
    }
    this.pasoActual = numero;
  }

  get puedeCompletar(): boolean {
    return this.enCurso && !this.gestionandoEvidencia && !this.cargandoPasos && this.guardando === null && !this.hayBorradoresPendientes && this.pasos.length > 0 && this.cumplidos === this.pasos.length && !this.completando;
  }

  get hayBorradoresPendientes(): boolean {
    return this.pasos.some((paso) => (this.borradores[paso.ejecucionPasoId] ?? paso.observacion ?? '').trim() !== (paso.observacion ?? ''));
  }

  etiquetaEstado(estado: string): string {
    if (estado === 'COMPLETADA') {
      return 'Completada';
    }
    if (estado === 'CANCELADA') {
      return 'Cancelada';
    }
    return 'En curso';
  }

  completarYAvanzar(paso: EjecucionPaso): void {
    if (!this.ejecucion || !this.enCurso || this.esAccion(paso) || paso.cumplido || this.operacionEnCurso() || this.cargandoPasos || !this.puedeMarcarPaso(paso)) return;
    this.cancelarAutosave(paso.ejecucionPasoId); this.guardando = paso.ejecucionPasoId; this.error = '';
    const observacion = (this.borradores[paso.ejecucionPasoId] ?? paso.observacion ?? '').trim();
    this.ejecuciones.siguiente(this.ejecucion.id, paso, observacion).subscribe({
      next: () => { this.guardando = null; this.alAutomatizacionActualizada({ avanzar: true, pasoId: paso.ejecucionPasoId }); },
      error: (e: Error) => { this.guardando = null; this.error = e.message; this.recargarPasos(); },
    });
  }
  esAccion(paso: EjecucionPaso): boolean { return ['VALIDACION_EXTERNA','SOLICITAR_EVIDENCIA','ENVIAR_CORREO','AUTOMATICO'].includes(paso.modoEjecucion ?? ''); }
  estadoPaso(paso: EjecucionPaso): string {
    if (paso.cumplido) return 'Completado';
    if(this.esAccion(paso) && (!paso.estado || paso.estado==='PENDIENTE'))return 'Pendiente de solicitud';
    return ({ESPERANDO_EVIDENCIA:'Esperando fotografías',ESPERANDO_CONFIRMACION:'Esperando respuesta',EVIDENCIA_RECIBIDA:'Evidencias disponibles',EN_VALIDACION:'Requiere validación',RECHAZADO:'Rechazado',ERROR:'Acción requerida'} as Record<string,string>)[paso.estado ?? ''] ?? 'Pendiente';
  }
  marcar(paso: EjecucionPaso, cumplido: boolean): void {
    if (!this.enCurso || !this.ejecucion || this.gestionandoEvidencia || this.cargandoPasos || this.guardando !== null || this.completando || this.cancelando ||
        (paso.modoEjecucion && paso.modoEjecucion !== 'MANUAL')) {
      return;
    }
    if (!this.puedeMarcarPaso(paso)) return;
    const ejecucionId = this.ejecucion.id;
    const version = this.version(paso.ejecucionPasoId);
    const observacion = (this.borradores[paso.ejecucionPasoId] ?? paso.observacion ?? '').trim();
    if (observacion.length > 300) return;
    this.cancelarAutosave(paso.ejecucionPasoId);
    this.guardando = paso.ejecucionPasoId;
    this.error = '';
    this.ejecuciones
      .actualizarPaso(ejecucionId, paso.ejecucionPasoId, {
        cumplido,
        observacion,
        versionEsperada: paso.version,
      })
      .subscribe({
        next: (actualizado) => {
          if (!this.vigente(paso.ejecucionPasoId, version)) {
            return;
          }
          this.reemplazar(actualizado);
          this.guardando = null;
          if (actualizado.cumplido && this.paso?.ejecucionPasoId === paso.ejecucionPasoId) this.avanzarAlPendiente();
          this.programarBorradoresPendientes();
        },
        error: (err: Error) => {
          if (!this.vigente(paso.ejecucionPasoId, version)) {
            return;
          }
          this.guardando = null;
          this.error = err.message;
          this.recargarPasos();
        },
      });
  }

  puedeMarcarPaso(paso: EjecucionPaso): boolean {
    const automatizada = this.pasos.some((p) => ['VALIDACION_EXTERNA','SOLICITAR_EVIDENCIA','ENVIAR_CORREO','AUTOMATICO'].includes(p.modoEjecucion ?? ''));
    return !automatizada || (!paso.cumplido && this.pasos.filter((p) => p.orden < paso.orden).every((p) => p.cumplido));
  }

  alEscribirObservacion(paso: EjecucionPaso, texto: string): void {
    if (this.observacionListaId === paso.ejecucionPasoId && (paso.observacion ?? '') !== texto.trim()) {
      this.observacionListaId = null;
    }
    this.cancelarAutosave(paso.ejecucionPasoId);
    if (!this.enCurso || texto.length > 300 || texto.trim() === (paso.observacion ?? '')) return;
    this.autosaves.set(paso.ejecucionPasoId, setTimeout(() => {
      this.autosaves.delete(paso.ejecucionPasoId);
      const actual = this.pasos.find((item) => item.ejecucionPasoId === paso.ejecucionPasoId);
      if (actual && !this.destruido && this.guardando === null) this.guardarObservacion(actual);
    }, 800));
  }

  guardarObservacion(paso: EjecucionPaso): void {
    const texto = (this.borradores[paso.ejecucionPasoId] ?? '').trim();
    if (!this.enCurso || !this.ejecucion || this.gestionandoEvidencia || this.cargandoPasos || this.guardando !== null || this.completando || this.cancelando || texto.length > 300 || texto === (paso.observacion ?? '')) {
      return;
    }
    const ejecucionId = this.ejecucion.id;
    const version = this.version(paso.ejecucionPasoId);
    this.cancelarAutosave(paso.ejecucionPasoId);
    this.guardando = paso.ejecucionPasoId;
    this.error = '';
    this.ejecuciones
      .actualizarPaso(ejecucionId, paso.ejecucionPasoId, {
        observacion: texto,
        versionEsperada: paso.version,
      })
      .subscribe({
        next: (actualizado) => {
          if (!this.vigente(paso.ejecucionPasoId, version)) {
            return;
          }
          this.reemplazar({ ...actualizado, observacion: actualizado.observacion ?? (texto || null) });
          this.guardando = null;
          this.observacionListaId = (this.borradores[paso.ejecucionPasoId] ?? '').trim() === texto ? paso.ejecucionPasoId : null;
          this.programarBorradoresPendientes();
        },
        error: (err: Error) => {
          if (!this.vigente(paso.ejecucionPasoId, version)) {
            return;
          }
          this.guardando = null;
          this.observacionListaId = null;
          this.error = err.message;
          this.recargarPasos();
        },
      });
  }

  private cancelarAutosave(id: number): void {
    const timer = this.autosaves.get(id);
    if (timer !== undefined) clearTimeout(timer);
    this.autosaves.delete(id);
  }

  private programarBorradoresPendientes(): void {
    if (this.destruido) return;
    for (const paso of this.pasos) {
      const borrador = this.borradores[paso.ejecucionPasoId];
      if (borrador !== undefined && borrador.trim() !== (paso.observacion ?? '')) this.alEscribirObservacion(paso, borrador);
    }
  }

  /** Al marcar un paso, pasa solo al siguiente que falte (si queda alguno). */
  private avanzarAlPendiente(): void {
    const total = this.pasos.length;
    if (total && this.pasos.every(p=>p.cumplido)) { this.pasoActual=total; return; }
    for (let salto = 1; salto <= total; salto++) {
      const indice = (this.pasoActual - 1 + salto) % total;
      if (!this.pasos[indice].cumplido) {
        this.pasoActual = indice + 1;
        return;
      }
    }
  }

  completar(): void {
    if (!this.puedeCompletar || !this.ejecucion) {
      return;
    }
    this.completando = true;
    this.error = '';
    this.ejecuciones.completar(this.ejecucion.id).subscribe({
      next: (ejecucion) => {
        this.ejecucion = ejecucion;
        this.completando = false;
      },
      error: (err: Error) => {
        this.completando = false;
        this.error = err.message;
        this.recargarPasos();
      },
    });
  }

  pedirCancelar(): void {
    if (!this.enCurso || this.gestionandoEvidencia || this.cancelando || this.guardando !== null) {
      return;
    }
    this.confirmarCancelacion = true;
  }

  cerrarCancelar(): void {
    if (this.cancelando) {
      return;
    }
    this.confirmarCancelacion = false;
    this.motivoCancelacion = '';
  }

  confirmarCancelar(): void {
    if (!this.ejecucion || !this.enCurso || this.gestionandoEvidencia || this.cancelando || this.guardando !== null) {
      return;
    }
    this.cancelando = true;
    this.error = '';
    this.ejecuciones.cancelar(this.ejecucion.id, { observaciones: this.motivoCancelacion }).subscribe({
      next: (ejecucion) => {
        this.ejecucion = ejecucion;
        this.cancelando = false;
        this.confirmarCancelacion = false;
        this.motivoCancelacion = '';
      },
      error: (err: Error) => {
        this.cancelando = false;
        this.error = err.message;
      },
    });
  }

  private cargar(id: number): void {
    this.error = '';
    // No mostrar datos privados de localStorage antes de que el servidor autorice esta sesión.
    this.loading = true;
    this.cargandoPasos = true;
    this.errorPasos = '';
    this.pasosConsultados = false;
    this.procedimientos.listar().subscribe({
      next: () => {
        if (this.ejecucion) {
          this.pintarNombre(this.ejecucion.procedimientoId);
        }
      },
      error: () => undefined,
    });
    const marca = this.ejecuciones.marcaPasos(id);

    this.ejecuciones.obtener(id, true).pipe(takeUntil(this.cancelarConsultas),finalize(()=>{this.loading=false;})).subscribe({
      next: (ejecucion) => {
        this.ejecucion = ejecucion;
        this.loading = false;
        this.pintarNombre(ejecucion.procedimientoId);
        this.ejecuciones.obtenerProcedimiento(ejecucion.procedimientoId).subscribe({
          next: (procedimiento) => (this.procedimiento = procedimiento),
          error: () => undefined,
        });
      },
      error: (err: Error) => {
        if (!this.ejecucion) {
          this.error = err.message;
        }
        this.loading = false;
      },
    });
    this.ejecuciones.obtenerPasos(id, true).pipe(takeUntil(this.cancelarConsultas),finalize(()=>{this.cargandoPasos=false;})).subscribe({
      next: (pasos) => {
        if (this.ejecuciones.marcaPasos(id) !== marca) {
          return;
        }
        this.cargandoPasos = false;
        this.errorPasos = '';
        this.pasosConsultados = true;
        this.ultimaSincronizacion = new Date();
        this.aplicarPasos(pasos, this.pasos.length === 0);
      },
      error: (err: Error) => {
        this.cargandoPasos = false;
        this.errorPasos = 'No fue posible cargar los pasos del procedimiento. Compruebe la conexión e intente nuevamente';
      },
    });
  }

  reintentarCarga(): void {
    if (this.loading || this.cargandoPasos || this.sincronizando || this.operacionEnCurso() || this.destruido) return;
    if (!this.ejecucion) {
      const id = Number(this.route.snapshot.paramMap.get('id'));
      if (id) this.cargar(id);
      return;
    }
    this.errorPasos = '';
    this.cargandoPasos = true;
    this.recargarPasos();
  }

  private pintarNombre(procedimientoId: number): void {
    const item = this.procedimientos.listaEnSesion().find((procedimiento) => procedimiento.id === procedimientoId);
    if (!item) {
      return;
    }
    this.procedimiento = {
      id: item.id,
      nombre: item.nombre,
      descripcion: item.descripcion,
      estado: item.estado,
    };
  }

  alAutomatizacionActualizada(evento: { avanzar: boolean; pasoId: number }): void {
    if (!this.ejecucion) return;
    this.gestionandoEvidencia = true;
    const id = this.ejecucion.id;
    this.ejecuciones.obtenerPasos(id, true).pipe(takeUntil(this.cancelarConsultas),finalize(()=>{this.gestionandoEvidencia=false;})).subscribe({
      next: (pasos) => {
        this.gestionandoEvidencia = false;
        this.errorPasos = '';
        this.pasosConsultados = true;
        this.aplicarPasos(pasos, false);
        this.ejecuciones.fijarPasos(id, pasos);
        if (evento.avanzar && this.paso?.ejecucionPasoId === evento.pasoId && this.paso.cumplido) this.avanzarAlPendiente();
        this.programarBorradoresPendientes();
      },
      error: (e: Error) => { this.gestionandoEvidencia = false; this.errorPasos = 'No fue posible cargar los pasos del procedimiento. Compruebe la conexión e intente nuevamente'; },
    });
  }

  private recargarPasos(): void {
    if (!this.ejecucion || this.sincronizando || this.destruido) {
      return;
    }
    const id = this.ejecucion.id;
    const marca = this.ejecuciones.marcaPasos(id);
    this.sincronizando = true;
    this.ejecuciones.obtenerPasos(id, true).pipe(takeUntil(this.cancelarConsultas),finalize(()=>{this.sincronizando=false;this.cargandoPasos=false;})).subscribe({
      next: (pasos) => {
        this.sincronizando = false;
        this.cargandoPasos = false;
        if (this.destruido || this.gestionandoEvidencia || this.guardando !== null || this.ejecuciones.marcaPasos(id) !== marca) return;
        this.aplicarPasos(pasos, false);
        this.errorPasos = '';
        this.pasosConsultados = true;
        this.ultimaSincronizacion = new Date();
        this.estadoSincronizacion = '';
      },
      error: () => { this.sincronizando = false; this.cargandoPasos = false; this.errorPasos = 'No fue posible cargar los pasos del procedimiento. Compruebe la conexión e intente nuevamente'; this.estadoSincronizacion = 'No fue posible actualizar. Se reintentará la consulta.'; },
    });
    this.ejecuciones.obtener(id, true).pipe(takeUntil(this.cancelarConsultas)).subscribe({
      next: (ejecucion) => { if (!this.destruido && this.enCurso && !this.completando && !this.cancelando) this.ejecucion = ejecucion; },
      error: () => undefined,
    });
  }

  private aplicarPasos(pasos: EjecucionPaso[], inicial: boolean): void {
    const anteriores = new Map(this.pasos.map((paso) => [paso.ejecucionPasoId, paso.observacion ?? '']));
    this.pasos = pasos;
    for (const paso of pasos) {
      const borrador = this.borradores[paso.ejecucionPasoId];
      if (inicial || borrador == null || borrador.trim() === anteriores.get(paso.ejecucionPasoId)) {
        this.borradores[paso.ejecucionPasoId] = paso.observacion ?? '';
      }
    }
    if (!inicial) {
      return;
    }
    const pendiente = pasos.findIndex((paso) => !paso.cumplido);
    this.pasoActual = pendiente >= 0 ? pendiente + 1 : 1;
  }

  private version(pasoId: number): number {
    const siguiente = (this.versiones.get(pasoId) ?? 0) + 1;
    this.versiones.set(pasoId, siguiente);
    return siguiente;
  }

  private vigente(pasoId: number, version: number): boolean {
    return !this.destruido && this.versiones.get(pasoId) === version;
  }

  private reemplazar(actualizado: EjecucionPaso): void {
    const anterior = this.pasos.find((paso) => paso.ejecucionPasoId === actualizado.ejecucionPasoId);
    const borrador = this.borradores[actualizado.ejecucionPasoId];
    this.pasos = this.pasos.map((paso) =>
      paso.ejecucionPasoId === actualizado.ejecucionPasoId ? actualizado : paso,
    );
    if (borrador === undefined || borrador.trim() === (anterior?.observacion ?? '')) {
      this.borradores[actualizado.ejecucionPasoId] = actualizado.observacion ?? '';
    }
    if (this.ejecucion) {
      this.ejecuciones.fijarPasos(this.ejecucion.id, this.pasos);
    }
  }
}
