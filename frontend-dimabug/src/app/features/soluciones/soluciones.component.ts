import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ConocimientoService } from '../../core/services/conocimiento.service';
import { SolucionItem } from '../../core/models/catalogo.model';
import { Conocimiento, TipoSolucion } from '../../core/models/conocimiento.model';
import { AyudaFranjaComponent } from '../../shared/ui/ayuda-franja.component';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';
@Component({
  selector: 'app-soluciones',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, RouterLink, LoadingModalComponent, AyudaFranjaComponent],
  templateUrl: './soluciones.component.html',
  styleUrls: ['../../shared/ui/catalogo-page.css', './soluciones.component.css'],
})
export class SolucionesComponent implements OnInit {
  readonly esAdministrador = inject(AuthService).isAdmin;
  private readonly conocimientos = inject(ConocimientoService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);

  q = this.route.snapshot.queryParamMap.get('q') || '';
  items: SolucionItem[] = [];
  guias: Conocimiento[] = [];
  loading = false;
  saving = false;
  error = '';
  formError = '';
  modalOpen = false;
  editItem: SolucionItem | null = null;
  detalle: SolucionItem | null = null;
  pendienteEstado: SolucionItem | null = null;
  operando = false;
  mensaje = '';

  ver(item: SolucionItem): void {
    this.detalle = item;
  }

  cerrarDetalle(): void {
    this.detalle = null;
  }

  form = this.fb.nonNullable.group({
    conocimientoId: [0, Validators.min(1)],
    titulo: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(150)]],
    descripcion: ['', Validators.required],
    tipo: ['PASOS' as TipoSolucion, Validators.required],
  });

  get filtrados(): SolucionItem[] {
    const term = this.q.trim().toLowerCase();
    if (!term) {
      return this.items;
    }
    return this.items.filter((item) =>
      [item.nombre, item.descripcion || '', item.pasos, item.anexos, item.asignaciones.join(' '), item.conocimientoTitulo || '']
        .join(' ')
        .toLowerCase()
        .includes(term),
    );
  }

  ngOnInit(): void {
    this.cargar();
  }

  cargar(force = false): void {
    this.loading = !this.items.length;
    this.error = '';
    let first = true;
    this.conocimientos
      .listarSolucionesCatalogo(force, this.esAdministrador())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (items) => {
          this.items = items;
          if (this.detalle) this.detalle = items.find(item => this.trackKey(item) === this.trackKey(this.detalle!)) ?? null;
          if (first) {
            first = false;
            this.loading = false;
          }
        },
        error: (err: Error) => {
          this.error = err.message;
          this.loading = false;
        },
      });
  }

  openCreate(): void {
    this.editItem = null;
    this.form.controls.conocimientoId.enable();
    this.formError = '';
    this.form.reset({ conocimientoId: 0, titulo: '', descripcion: '', tipo: 'PASOS' });
    this.modalOpen = true;
    this.cargarGuias();
  }

  openEdit(item: SolucionItem): void {
    if (!item.conocimientoId || item.activo === false) {
      return;
    }
    this.editItem = item;
    this.formError = '';
    this.form.reset({
      conocimientoId: item.conocimientoId,
      titulo: item.titulo || '',
      descripcion: item.descripcion ?? item.pasos,
      tipo: item.tipo === 'DERIVACION' ? 'DERIVACION' : 'PASOS',
    });
    this.form.controls.conocimientoId.disable();
    this.modalOpen = true;
    this.cargarGuias();
  }

  pedirEstado(item: SolucionItem): void {
    if (this.esAdministrador() && !this.operando) this.pendienteEstado = item;
  }

  cancelarEstado(): void {
    if (!this.operando) this.pendienteEstado = null;
  }

  confirmarEstado(): void {
    const item = this.pendienteEstado;
    if (!item?.conocimientoId || !this.esAdministrador() || this.operando) return;
    this.operando = true; this.error = ''; this.mensaje = '';
    const activo = item.activo === false;
    this.conocimientos.cambiarEstadoSolucion(item.conocimientoId, item.id, activo)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => {
          this.operando = false; this.pendienteEstado = null;
          this.mensaje = activo ? 'Solución reactivada.' : 'Solución desactivada. Se conserva su historial.';
          this.cargar(true);
        },
        error: (err: Error) => { this.operando = false; this.error = err.message; },
      });
  }

  cambiarAutoservicio(item: SolucionItem): void {
    if (!item.conocimientoId || !this.esAdministrador() || this.operando) return;
    this.operando = true; this.error = ''; this.mensaje = '';
    this.conocimientos.cambiarAutoservicioSolucion(item.conocimientoId, item.id, !item.autoservicio)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => { this.operando = false; this.mensaje = 'Clasificación de autoservicio actualizada.'; this.cargar(true); },
        error: (err: Error) => { this.operando = false; this.error = err.message; },
      });
  }

  closeModal(): void {
    if (this.saving) return;
    this.modalOpen = false;
    this.editItem = null;
    this.form.controls.conocimientoId.enable();
  }

  private cargarGuias(): void {
    this.conocimientos
      .listar()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (guias) => (this.guias = guias),
        error: () => (this.formError = 'No se pudieron cargar los conocimientos.'),
      });
  }

  save(): void {
    if (this.saving) return;
    this.formError = '';
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.formError = 'Elige un conocimiento, completa el título (máximo 150 caracteres) y las instrucciones.';
      return;
    }
    const raw = this.form.getRawValue();
    const conocimientoId = Number(raw.conocimientoId);
    const editando = this.editItem;
    const orden = editando?.orden || this.items.filter((item) => item.conocimientoId === conocimientoId).length + 1;
    const request = {
      titulo: raw.titulo.trim(),
      descripcion: raw.descripcion.trim(),
      tipo: raw.tipo,
      orden,
    };
    this.saving = true;
    const req$ = editando?.conocimientoId
      ? this.conocimientos.modificarSolucion(editando.conocimientoId, editando.id, request)
      : this.conocimientos.crearSolucion(conocimientoId, request);
    req$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving = false;
          this.closeModal();
          this.cargar(true);
        },
        error: (err: Error) => {
          this.saving = false;
          this.formError = err.message || 'No se pudo guardar la solución.';
        },
      });
  }

  trackKey(item: SolucionItem): string {
    return `${item.conocimientoId ?? 0}-${item.id}`;
  }

  recortar(texto: string, max = 160): string {
    if (!texto) {
      return '—';
    }
    return texto.length > max ? `${texto.slice(0, max)}…` : texto;
  }

  anexosLista(texto: string): string[] {
    if (!texto?.trim()) {
      return [];
    }
    return [texto.trim()];
  }
}
