import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink, convertToParamMap } from '@angular/router';
import { combineLatest, forkJoin, of, switchMap, catchError, tap, Subject, takeUntil } from 'rxjs';
import { AdminCatalogosApiService, CatalogoAdmin, TIPOS_CATALOGO_ADMIN, TipoCatalogoAdmin } from '../../core/services/admin-catalogos-api.service';
import { AyudaFranjaComponent } from '../../shared/ui/ayuda-franja.component';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';
@Component({
  selector: 'app-admin-catalogo', standalone: true,
  imports: [ReactiveFormsModule, RouterLink, AyudaFranjaComponent, LoadingModalComponent],
  templateUrl: './admin-catalogo.component.html',
  styleUrls: ['../../shared/ui/catalogo-page.css', './admin-catalogo.component.css'],
})
export class AdminCatalogoComponent implements OnInit {
  private readonly api = inject(AdminCatalogosApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  private readonly cambioContexto = new Subject<void>();
  tipo: TipoCatalogoAdmin = 'hardware';
  disponible = true;
  items: CatalogoAdmin[] = [];
  sistemas: CatalogoAdmin[] = [];
  hardwares: CatalogoAdmin[] = [];
  hardwareOrigenId: number | null = null;
  sistemaContextoId: number | null = null;
  crearYAsociar = false;
  sistemaCreado: CatalogoAdmin | null = null;
  creacionSinConfirmar = false;
  mensaje = '';
  loading = false;
  saving = false;
  error = '';
  formError = '';
  modalOpen = false;
  editId: number | null = null;
  pendienteEstado: CatalogoAdmin | null = null;
  private sistemasIniciales = new Set<number>();
  private sistemaInicialModulo: number | null = null;
  filtrosModulos = this.fb.group({
    sistemaId: this.fb.control<number | null>(null),
    estado: this.fb.nonNullable.control<'todos' | 'activos' | 'inactivos'>('todos'),
  });
  get itemsVisibles(): CatalogoAdmin[] {
    if (this.tipo !== 'modulos') return this.items;
    const { estado } = this.filtrosModulos.getRawValue();
    const sistemaId = this.sistemaContextoId ?? this.filtrosModulos.controls.sistemaId.value;
    return this.items.filter(item => (sistemaId === null || item.sistemaId === sistemaId)
      && (estado === 'todos' || item.activo === (estado === 'activos')));
  }
  limpiarFiltrosModulos(): void {
    this.filtrosModulos.reset({ sistemaId: this.sistemaContextoId, estado: 'todos' });
  }
  puedeElegirSistemaModulo(sistema: CatalogoAdmin): boolean {
    return sistema.activo || sistema.id === this.sistemaInicialModulo;
  }
  readonly titulos = { hardware: 'Hardware', sistemas: 'Sistemas', modulos: 'Módulos', frecuencias: 'Frecuencias' };
  form = this.fb.group({
    nombre: ['', [Validators.required, Validators.maxLength(120)]],
    sistemaOperativo: ['', Validators.maxLength(150)], descripcion: ['', Validators.maxLength(255)],
    sistemaId: this.fb.control<number | null>(null), orden: [1, [Validators.required, Validators.min(1)]],
    activo: [true], sistemaIds: this.fb.nonNullable.control<number[]>([]),
  });
  ngOnInit(): void {
    combineLatest([this.route.paramMap, this.route.queryParamMap ?? of(convertToParamMap({}))]).pipe(switchMap(([params, query]) => {
      this.cambioContexto.next(); this.saving = false;
      this.modalOpen = false; this.pendienteEstado = null; this.error = ''; this.items = []; this.sistemas = [];
      this.mensaje = ''; this.sistemaCreado = null; this.creacionSinConfirmar = false;
      this.hardwareOrigenId = this.idContexto(query.get('hardwareId'));
      this.sistemaContextoId = params.get('tipo') === 'modulos' ? this.idContexto(query.get('sistemaId')) : null;
      this.crearYAsociar = params.get('tipo') === 'sistemas' && query.get('crear') === '1' && this.hardwareOrigenId !== null;
      this.filtrosModulos.controls.sistemaId.enable();
      this.limpiarFiltrosModulos();
      if (this.sistemaContextoId) this.filtrosModulos.controls.sistemaId.disable();
      this.sistemaInicialModulo = null;
      const tipo = params.get('tipo');
      this.disponible = TIPOS_CATALOGO_ADMIN.includes(tipo as TipoCatalogoAdmin);
      if (!this.disponible) { this.loading = false; return of(null); }
      this.tipo = tipo as TipoCatalogoAdmin; this.loading = true;
      return this.carga().pipe(
        tap(data => {
          this.items = data.items; this.sistemas = data.sistemas; this.hardwares = data.hardwares;
          const editarId = this.idContexto(query.get('editarId'));
          if (editarId) {
            const item = this.items.find(i => i.id === editarId);
            if (item) this.abrir(item); else this.error = 'El registro solicitado no está disponible. Puedes consultar el listado global.';
          } else if (this.crearYAsociar) this.abrir();
        }), catchError((err: Error) => { this.error = err.message; return of(null); }));
    }), takeUntilDestroyed(this.destroyRef)).subscribe(data => {
      if (data) { this.items = data.items; this.sistemas = data.sistemas; } this.loading = false;
    });
  }
  private carga() {
    return forkJoin({ items: this.api.listar(this.tipo), sistemas:
      this.tipo === 'hardware' || this.tipo === 'modulos' ? this.api.listar('sistemas') : of([] as CatalogoAdmin[]),
      hardwares: this.tipo === 'sistemas' ? this.api.listar('hardware') : of([] as CatalogoAdmin[]) });
  }
  recargar(): void {
    this.loading = true; this.error = '';
    this.carga().pipe(takeUntil(this.cambioContexto), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: data => { this.items = data.items; this.sistemas = data.sistemas; this.hardwares = data.hardwares; this.loading = false; },
      error: (err: Error) => { this.error = err.message; this.loading = false; },
    });
  }
  abrir(item?: CatalogoAdmin): void {
    if (this.saving) return;
    // Una creación ya confirmada no debe convertirse en otro POST tras un fallo de asociación.
    if (!item && (this.sistemaCreado || this.creacionSinConfirmar)) {
      this.editId = null; this.modalOpen = true;
      this.formError = this.sistemaCreado
        ? `El sistema ya existe (ID ${this.sistemaCreado.id}). Reintenta solo su asociación o recupéralo desde Todos los Sistemas.`
        : 'No se pudo confirmar la creación. Revisa Todos los Sistemas antes de intentar otra alta.';
      if (this.sistemaCreado) { this.form.controls.nombre.setValue(this.sistemaCreado.nombre); this.form.disable(); }
      return;
    }
    this.editId = item?.id ?? null; this.formError = '';
    this.sistemasIniciales = new Set(item?.sistemaIds ?? []);
    this.sistemaInicialModulo = this.tipo === 'modulos' ? item?.sistemaId ?? null : null;
    this.form.enable();
    this.form.reset({ nombre: item?.nombre ?? '', activo: item?.activo ?? true,
      sistemaOperativo: item?.sistemaOperativo ?? '', descripcion: item?.descripcion ?? '',
      sistemaId: item?.sistemaId ?? this.sistemaContextoId, orden: item?.orden ?? 1, sistemaIds: [...(item?.sistemaIds ?? [])] });
    if (this.tipo === 'modulos' && ((item && this.enContexto) || this.sistemaContextoId)) this.form.controls.sistemaId.disable();
    else this.form.controls.sistemaId.enable();
    if (!item && this.crearYAsociar) this.form.controls.activo.disable();
    this.form.controls.nombre.setValidators([Validators.required, Validators.maxLength(this.tipo === 'frecuencias' ? 50 : this.tipo === 'modulos' ? 120 : 100)]);
    this.form.controls.nombre.updateValueAndValidity();
    this.modalOpen = true;
  }
  puedeSeleccionarSistema(sistema: CatalogoAdmin): boolean {
    return sistema.activo || this.sistemasIniciales.has(sistema.id);
  }
  asociar(id: number, evento: Event): void {
    const sistema = this.sistemas.find(s => s.id === id);
    const marcado = (evento.target as HTMLInputElement).checked;
    if (this.tipo !== 'hardware' || !sistema || (marcado && !this.puedeSeleccionarSistema(sistema))) return;
    const actual = this.form.controls.sistemaIds.value;
    this.form.controls.sistemaIds.setValue(marcado ? [...new Set([...actual, id])] : actual.filter(v => v !== id));
    this.form.controls.sistemaIds.markAsDirty();
  }
  guardar(): void {
    if (this.saving) return;
    if (this.crearYAsociar && this.editId === null && (this.sistemaCreado || this.creacionSinConfirmar)) return;
    const v = this.form.getRawValue();
    if (this.crearYAsociar && this.editId === null && !v.activo) {
      this.formError = 'El sistema nuevo debe estar activo para asociarlo al Hardware.'; return;
    }
    if (this.tipo === 'modulos' && this.editId !== null && this.enContexto && v.sistemaId !== this.sistemaInicialModulo) {
      this.formError = 'No puedes trasladar un módulo a otro sistema desde este flujo.'; return;
    }
    if (this.tipo === 'modulos' && this.editId === null && this.sistemaContextoId && v.sistemaId !== this.sistemaContextoId) {
      this.formError = 'El módulo debe pertenecer al sistema seleccionado.'; return;
    }
    if (this.form.invalid || !v.nombre?.trim() || (this.tipo === 'modulos' && (!v.sistemaId || !this.sistemas.some(s => s.id === v.sistemaId && this.puedeElegirSistemaModulo(s))))) {
      this.form.markAllAsTouched(); this.formError = 'Completa el nombre y los campos obligatorios.'; return;
    }
    this.saving = true; this.formError = '';
    this.api.guardar(this.tipo, this.editId, { nombre: v.nombre.trim(), activo: v.activo ?? true,
      sistemaOperativo: v.sistemaOperativo?.trim() || null, descripcion: v.descripcion?.trim() || null,
      sistemaId: this.tipo === 'modulos' ? v.sistemaId : null,
      orden: this.tipo === 'frecuencias' ? v.orden : null, sistemaIds: this.tipo === 'hardware' ? v.sistemaIds : [] })
      .pipe(takeUntil(this.cambioContexto), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: guardado => {
          if (this.crearYAsociar && this.editId === null) {
            this.sistemaCreado = guardado; this.form.disable(); this.asociarSistemaCreado();
          } else { this.saving = false; this.modalOpen = false; this.recargar(); }
        },
        error: (err: Error) => {
          this.saving = false;
          if (this.crearYAsociar && this.editId === null) {
            this.creacionSinConfirmar = true;
            this.formError = `No se pudo confirmar la creación. Revisa Todos los Sistemas antes de intentar otra alta. ${err.message}`;
          } else this.formError = err.message;
        },
      });
  }
  confirmarEstado(): void {
    const item = this.pendienteEstado; if (!item || this.saving) return;
    this.saving = true; this.error = '';
    this.api.cambiarEstado(this.tipo, item.id, !item.activo).pipe(takeUntil(this.cambioContexto), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.saving = false; this.pendienteEstado = null; this.recargar(); },
      error: (err: Error) => { this.saving = false; this.pendienteEstado = null; this.error = err.message; },
    });
  }
  nombreSistema(id: number | null): string { return this.sistemas.find(s => s.id === id)?.nombre ?? '—'; }
  nombresSistemas(ids: number[]): string { return ids.map(id => this.nombreSistema(id)).join(', ') || 'Sin asociaciones'; }
  get enContexto(): boolean { return this.hardwareOrigenId !== null || this.sistemaContextoId !== null; }
  hardwareDelSistema(id: number): CatalogoAdmin[] { return this.hardwares.filter(h => h.sistemaIds.includes(id)); }
  private idContexto(value: string | null): number | null {
    const id = Number(value); return Number.isSafeInteger(id) && id > 0 ? id : null;
  }
  reintentarAsociacion(): void {
    if (this.saving || !this.sistemaCreado) return;
    this.saving = true; this.asociarSistemaCreado();
  }
  private asociarSistemaCreado(): void {
    const sistema = this.sistemaCreado;
    if (!sistema || !this.hardwareOrigenId) { this.saving = false; return; }
    this.api.obtener('hardware', this.hardwareOrigenId).pipe(switchMap(hardware => {
      const { id, ...datos } = hardware;
      return this.api.guardar('hardware', id, { ...datos, sistemaIds: [...new Set([...hardware.sistemaIds, sistema.id])] });
    }), takeUntil(this.cambioContexto), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving = false; this.modalOpen = false;
        this.mensaje = `Sistema ${sistema.nombre} (ID ${sistema.id}) creado y asociado al Hardware.`;
        this.recargar();
      },
      error: (err: Error) => {
        this.saving = false;
        this.formError = `El sistema ${sistema.nombre} ya existe (ID ${sistema.id}), pero no se pudo confirmar su asociación. Puedes recuperarlo en Todos los Sistemas. ${err.message}`;
        this.recargar();
      },
    });
  }
}
