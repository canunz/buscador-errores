import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, switchMap, tap } from 'rxjs';
import { CatalogoRef } from '../../core/models/conocimiento.model';
import {
  ConocimientoSugerido,
  PRIORIDADES_TICKET,
  PRIORIDAD_INFO,
  PrioridadTicket,
} from '../../core/models/ticket.model';
import { AuthService } from '../../core/services/auth.service';
import { ClasificacionService } from '../../core/services/clasificacion.service';
import { TicketService } from '../../core/services/ticket.service';
import { SelectorAdjuntosComponent } from './adjuntos/selector-adjuntos.component';

@Component({
  selector: 'app-crear-ticket',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, SelectorAdjuntosComponent],
  templateUrl: './crear-ticket.component.html',
  styleUrls: ['./tickets-comun.css', './crear-ticket.component.css'],
})
export class CrearTicketComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly tickets = inject(TicketService);
  private readonly clasificacion = inject(ClasificacionService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly prioridades = PRIORIDADES_TICKET;
  readonly prioridadInfo = PRIORIDAD_INFO;
  readonly maxTitulo = 150;
  readonly volverA = this.auth.isStaff() ? '/tickets/bandeja' : '/tickets';

  hardwares: CatalogoRef[] = [];
  sistemas: CatalogoRef[] = [];
  modulos: CatalogoRef[] = [];

  sugerencias: ConocimientoSugerido[] = [];
  buscandoSugerencias = false;
  textoBuscado = '';

  enviando = false;
  error = '';

  form = this.fb.nonNullable.group({
    titulo: ['', [Validators.required, Validators.maxLength(this.maxTitulo)]],
    descripcion: ['', Validators.required],
    prioridad: this.fb.nonNullable.control<PrioridadTicket>('MEDIA'),
    hardwareId: this.fb.control<number | null>(null),
    sistemaId: this.fb.control<number | null>({ value: null, disabled: true }),
    moduloId: this.fb.control<number | null>({ value: null, disabled: true }),
  });

  ngOnInit(): void {
    this.hardwares = this.clasificacion.snapshotHardware();
    this.clasificacion.listarHardware().subscribe({
      next: (lista) => (this.hardwares = lista),
      error: () => undefined,
    });

    this.form.controls.hardwareId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((id) => this.alCambiarHardware(id));
    this.form.controls.sistemaId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((id) => this.alCambiarSistema(id));

    this.form.controls.titulo.valueChanges
      .pipe(
        map((texto) => texto.trim()),
        debounceTime(400),
        distinctUntilChanged(),
        tap((texto) => {
          this.textoBuscado = texto;
          this.buscandoSugerencias = texto.length >= 3;
          if (texto.length < 3) this.sugerencias = [];
        }),
        switchMap((texto) =>
          texto.length < 3 ? of([] as ConocimientoSugerido[]) : this.tickets.sugerencias(texto).pipe(catchError(() => of([]))),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((lista) => {
        this.sugerencias = lista;
        this.buscandoSugerencias = false;
      });
  }

  get largoTitulo(): number {
    return this.form.controls.titulo.value.length;
  }

  invalido(campo: 'titulo' | 'descripcion'): boolean {
    const control = this.form.controls[campo];
    return control.invalid && (control.touched || control.dirty);
  }

  elegirPrioridad(prioridad: PrioridadTicket): void {
    this.form.controls.prioridad.setValue(prioridad);
  }

  ubicacion(s: ConocimientoSugerido): string {
    return [s.hardware, s.sistema, s.modulo].filter(Boolean).join(' · ');
  }

  /** Capturas o videos del problema; se suben apenas se crea el ticket. */
  archivos: File[] = [];
  subiendoArchivos = false;

  enviar(): void {
    if (this.form.invalid || this.enviando) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.enviando = true;
    this.error = '';
    this.tickets
      .crear({
        titulo: v.titulo.trim(),
        descripcion: v.descripcion.trim(),
        prioridad: v.prioridad,
        hardwareId: v.hardwareId,
        sistemaId: v.sistemaId,
        moduloId: v.moduloId,
      })
      .subscribe({
        next: (ticket) => {
          const ir = () => this.router.navigate(['/tickets', ticket.id], { queryParams: { creado: 1 } });
          if (!this.archivos.length) {
            ir();
            return;
          }
          this.subiendoArchivos = true;
          // Si algún archivo falla, el ticket ya existe: se abre igual y desde ahí se puede volver a adjuntar.
          this.tickets.subirAdjuntos(ticket.id, this.archivos).subscribe({ next: () => ir(), error: () => ir() });
        },
        error: (err: Error) => {
          this.error = err.message;
          this.enviando = false;
        },
      });
  }

  private alCambiarHardware(hardwareId: number | null): void {
    const { sistemaId, moduloId } = this.form.controls;
    sistemaId.setValue(null, { emitEvent: false });
    moduloId.setValue(null, { emitEvent: false });
    moduloId.disable({ emitEvent: false });
    this.modulos = [];
    if (!hardwareId) {
      this.sistemas = [];
      sistemaId.disable({ emitEvent: false });
      return;
    }
    sistemaId.enable({ emitEvent: false });
    this.sistemas = this.clasificacion.snapshotSistemas(hardwareId);
    this.clasificacion.sistemasDeHardware(hardwareId).subscribe({
      next: (lista) => (this.sistemas = lista),
      error: (err: Error) => (this.error = err.message),
    });
  }

  private alCambiarSistema(sistemaId: number | null): void {
    const { moduloId } = this.form.controls;
    moduloId.setValue(null, { emitEvent: false });
    if (!sistemaId) {
      this.modulos = [];
      moduloId.disable({ emitEvent: false });
      return;
    }
    moduloId.enable({ emitEvent: false });
    this.modulos = this.clasificacion.snapshotModulos(sistemaId);
    this.clasificacion.modulosDeSistema(sistemaId, this.modulos.length === 0).subscribe({
      next: (lista) => (this.modulos = lista),
      error: (err: Error) => (this.error = err.message),
    });
  }
}
