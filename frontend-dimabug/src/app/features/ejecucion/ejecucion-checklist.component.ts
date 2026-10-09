import { DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Ejecucion, EjecucionPaso, ProcedimientoResumen } from '../../core/models/ejecucion.model';
import { EjecucionService } from '../../core/services/ejecucion.service';
import { ProcedimientoService } from '../../core/services/procedimiento.service';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';

@Component({
  selector: 'app-ejecucion-checklist',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink, LoadingModalComponent],
  templateUrl: './ejecucion-checklist.component.html',
  styleUrl: './ejecucion-checklist.component.css',
})
export class EjecucionChecklistComponent implements OnInit {
  private readonly ejecuciones = inject(EjecucionService);
  private readonly procedimientos = inject(ProcedimientoService);
  private readonly route = inject(ActivatedRoute);

  ejecucion: Ejecucion | null = null;
  procedimiento: ProcedimientoResumen | null = null;
  pasos: EjecucionPaso[] = [];
  borradores: Record<number, string> = {};
  loading = false;
  cargandoPasos = false;
  error = '';
  guardando: number | null = null;
  completando = false;
  cancelando = false;
  confirmarCancelacion = false;
  motivoCancelacion = '';
  pasoActual = 1;
  observacionListaId: number | null = null;
  private readonly versiones = new Map<number, number>();

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      this.error = 'La ejecución solicitada no está disponible.';
      this.loading = false;
      return;
    }
    this.cargar(id);
  }

  get enCurso(): boolean {
    return this.ejecucion?.estado === 'EN_CURSO';
  }

  get cumplidos(): number {
    return this.pasos.filter((paso) => paso.cumplido).length;
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
    if (numero < 1 || numero > this.pasos.length) {
      return;
    }
    this.pasoActual = numero;
  }

  get puedeCompletar(): boolean {
    return this.enCurso && this.pasos.length > 0 && this.cumplidos === this.pasos.length && !this.completando;
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

  marcar(paso: EjecucionPaso, cumplido: boolean): void {
    if (!this.enCurso || !this.ejecucion || this.completando || this.cancelando) {
      return;
    }
    const ejecucionId = this.ejecucion.id;
    const version = this.version(paso.ejecucionPasoId);
    const previo = this.pasos;
    const observacion = (this.borradores[paso.ejecucionPasoId] ?? paso.observacion ?? '').trim();
    this.pasos = this.pasos.map((item) =>
      item.ejecucionPasoId === paso.ejecucionPasoId ? { ...item, cumplido, observacion: observacion || null } : item,
    );
    this.ejecuciones.fijarPasos(ejecucionId, this.pasos);
    this.error = '';
    this.ejecuciones
      .actualizarPaso(ejecucionId, paso.ejecucionPasoId, {
        cumplido,
        observacion,
      })
      .subscribe({
        next: (actualizado) => {
          if (!this.vigente(paso.ejecucionPasoId, version)) {
            return;
          }
          this.reemplazar(actualizado);
        },
        error: (err: Error) => {
          if (!this.vigente(paso.ejecucionPasoId, version)) {
            return;
          }
          this.pasos = previo;
          this.ejecuciones.fijarPasos(ejecucionId, previo);
          this.error = err.message;
        },
      });
  }

  alEscribirObservacion(paso: EjecucionPaso, texto: string): void {
    if (this.observacionListaId === paso.ejecucionPasoId && (paso.observacion ?? '') !== texto.trim()) {
      this.observacionListaId = null;
    }
  }

  guardarObservacion(paso: EjecucionPaso): void {
    const texto = (this.borradores[paso.ejecucionPasoId] ?? '').trim();
    if (!this.enCurso || !this.ejecucion || texto.length > 300) {
      return;
    }
    const ejecucionId = this.ejecucion.id;
    const version = this.version(paso.ejecucionPasoId);
    const previo = this.pasos;
    this.borradores[paso.ejecucionPasoId] = texto;
    this.pasos = this.pasos.map((item) =>
      item.ejecucionPasoId === paso.ejecucionPasoId ? { ...item, observacion: texto || null } : item,
    );
    this.ejecuciones.fijarPasos(ejecucionId, this.pasos);
    this.observacionListaId = paso.ejecucionPasoId;
    this.error = '';
    this.ejecuciones
      .actualizarPaso(ejecucionId, paso.ejecucionPasoId, {
        cumplido: paso.cumplido,
        observacion: texto,
      })
      .subscribe({
        next: (actualizado) => {
          if (!this.vigente(paso.ejecucionPasoId, version)) {
            return;
          }
          this.reemplazar({ ...actualizado, observacion: actualizado.observacion ?? (texto || null) });
        },
        error: (err: Error) => {
          if (!this.vigente(paso.ejecucionPasoId, version)) {
            return;
          }
          this.pasos = previo;
          this.ejecuciones.fijarPasos(ejecucionId, previo);
          this.observacionListaId = null;
          this.error = err.message;
        },
      });
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
    if (!this.enCurso || this.cancelando) {
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
    if (!this.ejecucion || !this.enCurso || this.cancelando) {
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
    const guardada = this.ejecuciones.detalleEnSesion(id) ?? this.ejecuciones.listaEnSesion().find((item) => item.id === id) ?? null;
    if (guardada) {
      this.ejecucion = guardada;
      this.pintarNombre(guardada.procedimientoId);
    }
    const pasosGuardados = this.ejecuciones.pasosEnSesion(id);
    if (pasosGuardados?.length) {
      this.aplicarPasos(pasosGuardados, true);
    }
    this.loading = !guardada;
    this.cargandoPasos = !pasosGuardados?.length;
    this.procedimientos.listar().subscribe({
      next: () => {
        if (this.ejecucion) {
          this.pintarNombre(this.ejecucion.procedimientoId);
        }
      },
      error: () => undefined,
    });
    const marca = this.ejecuciones.marcaPasos(id);

    this.ejecuciones.obtener(id).subscribe({
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
    this.ejecuciones.obtenerPasos(id).subscribe({
      next: (pasos) => {
        if (this.ejecuciones.marcaPasos(id) !== marca) {
          return;
        }
        this.cargandoPasos = false;
        this.aplicarPasos(pasos, this.pasos.length === 0);
      },
      error: (err: Error) => {
        this.cargandoPasos = false;
        if (!this.pasos.length) {
          this.error = err.message;
        }
      },
    });
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

  private recargarPasos(): void {
    if (!this.ejecucion) {
      return;
    }
    this.ejecuciones.obtenerPasos(this.ejecucion.id).subscribe({
      next: (pasos) => this.aplicarPasos(pasos, false),
      error: (err: Error) => (this.error = err.message),
    });
  }

  private aplicarPasos(pasos: EjecucionPaso[], inicial: boolean): void {
    this.pasos = pasos;
    for (const paso of pasos) {
      if (inicial || this.borradores[paso.ejecucionPasoId] == null) {
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
    return this.versiones.get(pasoId) === version;
  }

  private reemplazar(actualizado: EjecucionPaso): void {
    this.pasos = this.pasos.map((paso) =>
      paso.ejecucionPasoId === actualizado.ejecucionPasoId ? actualizado : paso,
    );
    if (actualizado.observacion != null) {
      this.borradores[actualizado.ejecucionPasoId] = actualizado.observacion;
    }
    if (this.ejecucion) {
      this.ejecuciones.fijarPasos(this.ejecucion.id, this.pasos);
    }
  }
}
