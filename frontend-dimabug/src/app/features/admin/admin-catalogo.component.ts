import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin, of, switchMap, catchError } from 'rxjs';
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
  tipo: TipoCatalogoAdmin = 'hardware';
  disponible = true;
  items: CatalogoAdmin[] = [];
  sistemas: CatalogoAdmin[] = [];
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
    const { sistemaId, estado } = this.filtrosModulos.getRawValue();
    return this.items.filter(item => (sistemaId === null || item.sistemaId === sistemaId)
      && (estado === 'todos' || item.activo === (estado === 'activos')));
  }
  limpiarFiltrosModulos(): void {
    this.filtrosModulos.reset({ sistemaId: null, estado: 'todos' });
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
    this.route.paramMap.pipe(switchMap(params => {
      this.modalOpen = false; this.pendienteEstado = null; this.error = ''; this.items = []; this.sistemas = [];
      this.limpiarFiltrosModulos();
      this.sistemaInicialModulo = null;
      const tipo = params.get('tipo');
      this.disponible = TIPOS_CATALOGO_ADMIN.includes(tipo as TipoCatalogoAdmin);
      if (!this.disponible) { this.loading = false; return of(null); }
      this.tipo = tipo as TipoCatalogoAdmin; this.loading = true;
      return this.carga().pipe(catchError((err: Error) => { this.error = err.message; return of(null); }));
    }), takeUntilDestroyed(this.destroyRef)).subscribe(data => {
      if (data) { this.items = data.items; this.sistemas = data.sistemas; } this.loading = false;
    });
  }
  private carga() {
    return forkJoin({ items: this.api.listar(this.tipo), sistemas:
      this.tipo === 'hardware' || this.tipo === 'modulos' ? this.api.listar('sistemas') : of([] as CatalogoAdmin[]) });
  }
  recargar(): void {
    this.loading = true; this.error = '';
    this.carga().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: data => { this.items = data.items; this.sistemas = data.sistemas; this.loading = false; },
      error: (err: Error) => { this.error = err.message; this.loading = false; },
    });
  }
  abrir(item?: CatalogoAdmin): void {
    this.editId = item?.id ?? null; this.formError = '';
    this.sistemasIniciales = new Set(item?.sistemaIds ?? []);
    this.sistemaInicialModulo = this.tipo === 'modulos' ? item?.sistemaId ?? null : null;
    this.form.reset({ nombre: item?.nombre ?? '', activo: item?.activo ?? true,
      sistemaOperativo: item?.sistemaOperativo ?? '', descripcion: item?.descripcion ?? '',
      sistemaId: item?.sistemaId ?? null, orden: item?.orden ?? 1, sistemaIds: [...(item?.sistemaIds ?? [])] });
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
    const v = this.form.getRawValue();
    if (this.form.invalid || !v.nombre?.trim() || (this.tipo === 'modulos' && (!v.sistemaId || !this.sistemas.some(s => s.id === v.sistemaId && this.puedeElegirSistemaModulo(s))))) {
      this.form.markAllAsTouched(); this.formError = 'Completa el nombre y los campos obligatorios.'; return;
    }
    this.saving = true; this.formError = '';
    this.api.guardar(this.tipo, this.editId, { nombre: v.nombre.trim(), activo: v.activo ?? true,
      sistemaOperativo: v.sistemaOperativo?.trim() || null, descripcion: v.descripcion?.trim() || null,
      sistemaId: this.tipo === 'modulos' ? v.sistemaId : null,
      orden: this.tipo === 'frecuencias' ? v.orden : null, sistemaIds: this.tipo === 'hardware' ? v.sistemaIds : [] })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => { this.saving = false; this.modalOpen = false; this.recargar(); },
        error: (err: Error) => { this.saving = false; this.formError = err.message; },
      });
  }
  confirmarEstado(): void {
    const item = this.pendienteEstado; if (!item || this.saving) return;
    this.saving = true; this.error = '';
    this.api.cambiarEstado(this.tipo, item.id, !item.activo).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.saving = false; this.pendienteEstado = null; this.recargar(); },
      error: (err: Error) => { this.saving = false; this.pendienteEstado = null; this.error = err.message; },
    });
  }
  nombreSistema(id: number | null): string { return this.sistemas.find(s => s.id === id)?.nombre ?? '—'; }
  nombresSistemas(ids: number[]): string { return ids.map(id => this.nombreSistema(id)).join(', ') || 'Sin asociaciones'; }
}
