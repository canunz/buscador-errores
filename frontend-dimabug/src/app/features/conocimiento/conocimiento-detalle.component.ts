import { DatePipe, NgClass } from '@angular/common';
import { Component, DestroyRef, Injector, Input, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { apiUrl } from '../../core/config/api';
import { AdminPruebasApiService } from '../../core/services/admin-pruebas-api.service';
import { PruebaItem } from '../../core/models/catalogo.model';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  AsignacionSolucion,
  CatalogoRef,
  Conocimiento,
  ItemOrdenado,
  MaterialApoyo,
  PruebaAsociada,
  PruebaCatalogo,
  ResponsableRef,
  Solucion,
  TipoMaterial,
  TipoSolucion,
  etiquetaEstadoConocimiento,
} from '../../core/models/conocimiento.model';
import { AuthService } from '../../core/services/auth.service';
import { ConocimientoService, SolucionCatalogoItem } from '../../core/services/conocimiento.service';
import { FavoritosService } from '../../core/services/favoritos.service';
import { OrganizacionService } from '../../core/services/organizacion.service';
import {
  OrigenMaterial,
  acceptArchivoMaterial,
  admiteUploadLocal,
  muestraUrlMaterial,
  origenDesdeAdjunto,
  validarMaterialFormulario,
} from '../../core/material/material-archivo';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';
import { SolucionEfectividadComponent } from './solucion-efectividad.component';

type Seccion = 'sintomas' | 'causas' | 'pruebas' | 'soluciones' | 'materiales' | 'asignaciones';

@Component({
  selector: 'app-conocimiento-detalle',
  standalone: true,
  imports: [
    DatePipe,
    NgClass,
    RouterLink,
    ReactiveFormsModule,
    LoadingModalComponent,
    SolucionEfectividadComponent,
    ],
  templateUrl: './conocimiento-detalle.component.html',
  styleUrl: './conocimiento-detalle.component.css',
})
export class ConocimientoDetalleComponent implements OnInit {
  private readonly conocimientosApi = inject(ConocimientoService);
  private readonly auth = inject(AuthService);
  private readonly organizacion = inject(OrganizacionService);
  private readonly favoritosService = inject(FavoritosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);

  @Input() embebido = false;

  readonly esAdministrador = this.auth.isAdmin;
  readonly puedeGestionar = this.auth.isStaff;
  readonly etiquetaEstado = etiquetaEstadoConocimiento;

  item: Conocimiento | null = null;
  sintomas: ItemOrdenado[] = [];
  causas: ItemOrdenado[] = [];
  pruebas: PruebaAsociada[] = [];
  soluciones: Solucion[] = [];
  materiales: MaterialApoyo[] = [];
  asignacionesPorSolucion: Record<number, AsignacionSolucion[]> = {};

  pruebasCatalogo: PruebaCatalogo[] = [];
  departamentos: CatalogoRef[] = [];
  responsables: ResponsableRef[] = [];

  loading = false;
  loadingSintomas = false;
  loadingCausas = false;
  loadingPruebas = false;
  loadingSoluciones = false;
  loadingMateriales = false;
  error = '';
  sectionError = '';
  sectionSuccess = '';
  publishing = false;
  eliminando = false;
  confirmarEliminacion = false;
  confirmarQuitarFavorito = false;
  saving: Seccion | null = null;
  copiado = false;
  reutilizarAbierto = false;
  cargandoCatalogoSoluciones = false;
  copiandoSolucion = false;
  copiaCompletada = false;
  copiaGuardada: Solucion | null = null;
  copiaError = '';
  solucionesReutilizables: SolucionCatalogoItem[] = [];
  solucionSeleccionada: SolucionCatalogoItem | null = null;
  readonly reutilizarForm = this.fb.nonNullable.group({ busqueda: [''], confirmar: [false], autoservicio: [false] });
  get solucionesReutilizablesFiltradas(): SolucionCatalogoItem[] {
    const q = this.reutilizarForm.controls.busqueda.value.trim().toLocaleLowerCase('es');
    return this.solucionesReutilizables.filter(s => !q || [s.titulo, s.descripcion, s.conocimientoTitulo, s.tipo]
      .join(' ').toLocaleLowerCase('es').includes(q));
  }
  abrirReutilizarSolucion(): void {
    if (!this.esAdministrador() || !this.item || this.item.estado === 'ELIMINADO' || this.copiandoSolucion) return;
    this.reutilizarAbierto = true; this.copiaError = ''; this.copiaCompletada = false;
    this.copiaGuardada = null; this.solucionesReutilizables = [];
    this.solucionSeleccionada = null; this.reutilizarForm.reset({ busqueda: '', confirmar: false, autoservicio: false });
    this.cargandoCatalogoSoluciones = true;
    const destino = this.item.id;
    this.conocimientosApi.listarSolucionesCatalogo(true, true, true).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: items => { this.cargandoCatalogoSoluciones = false;
        if (!this.reutilizarAbierto || this.item?.id !== destino) return;
        this.solucionesReutilizables = items.filter(s => s.conocimientoId !== destino);
      }, error: (e: Error) => { this.cargandoCatalogoSoluciones = false; this.copiaError = e.message; },
    });
  }
  seleccionarSolucionReutilizable(s: SolucionCatalogoItem): void {
    if (this.copiandoSolucion || this.copiaCompletada) return;
    this.solucionSeleccionada = s; this.copiaError = '';
    this.reutilizarForm.patchValue({ confirmar: false, autoservicio: false });
  }
  cerrarReutilizarSolucion(): void {
    if (!this.copiandoSolucion) this.reutilizarAbierto = false;
  }
  confirmarCopiaSolucion(): void {
    const s = this.solucionSeleccionada; const destino = this.item;
    if (!this.esAdministrador() || !s || !destino || this.copiandoSolucion || this.copiaCompletada
        || !this.reutilizarForm.controls.confirmar.value) return;
    this.copiandoSolucion = true; this.copiaError = '';
    this.conocimientosApi.copiarSolucion(destino.id, s.conocimientoId, s.id, this.reutilizarForm.controls.autoservicio.value)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: guardada => {
        this.copiaGuardada = guardada; this.copiaCompletada = true;
        // Después de un POST exitoso, solo se reintenta la lectura; nunca se repite la creación.
        this.recargarTrasCopia(destino.id);
      }, error: (e: Error) => { this.copiandoSolucion = false; this.copiaError = e.message; } });
  }
  recargarTrasCopia(destinoId = this.item?.id): void {
    if (!destinoId || !this.copiaCompletada) return;
    this.copiandoSolucion = true;
    this.conocimientosApi.listarSoluciones(destinoId, true).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: items => {
        this.copiandoSolucion = false;
        if (this.item?.id !== destinoId) { this.reutilizarAbierto = false; return; }
        this.soluciones = items; this.publicarLista(`soluciones:${destinoId}`, items); this.cargarAsignaciones(destinoId, items);
        this.reutilizarAbierto = false; this.ok(this.copiaGuardada?.activo === false
          ? 'Solución inactiva copiada. Puedes consultarla en la administración de Soluciones; no aparece en Resolver ni en el chatbot.'
          : 'Solución copiada de forma independiente. No se copiaron asignaciones ni historial.');
      }, error: (e: Error) => { this.copiandoSolucion = false; this.copiaError = `La solución ya se copió. Falta recargar el destino: ${e.message}`; },
    });
  }
  modoPrueba: 'buscar' | 'crear' = 'buscar';
  cargandoPruebasCatalogo = false;
  pruebaBusqueda = this.fb.nonNullable.control('');
  pruebaNuevaForm = this.fb.nonNullable.group({
    descripcion: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(500)]],
    resultadoEsperado: ['', Validators.maxLength(500)],
  });
  pruebaCreada: PruebaItem | null = null;
  pruebaDestinoPendienteId: number | null = null;
  creacionPruebaIncierta = false;
  get pruebasFiltradas(): PruebaCatalogo[] {
    const q = this.pruebaBusqueda.value.trim().toLocaleLowerCase('es');
    return this.pruebasCatalogo.filter(p => !q || `${p.descripcion} ${p.resultadoEsperado || ''}`.toLocaleLowerCase('es').includes(q));
  }
  get pruebaSeleccionada(): PruebaCatalogo | undefined { return this.pruebasCatalogo.find(p => p.id === this.pruebaForm.controls.pruebaId.value); }
  abrirBuscarPrueba(): void { if (!this.puedeGestionar() || this.saving === 'pruebas') return; this.modoPrueba = 'buscar'; this.abrirFormulario('pruebas'); }
  abrirNuevaPrueba(): void {
    if (!this.esAdministrador() || this.saving === 'pruebas') return;
    this.modoPrueba = 'crear'; this.formAbierto.pruebas = true; this.sectionError = '';
  }
  guardarNuevaPruebaYAsociar(): void {
    const destino = this.conocimientoId;
    if (!this.esAdministrador() || !destino || this.saving === 'pruebas') return;
    if (this.pruebaDestinoPendienteId != null && this.pruebaDestinoPendienteId !== destino) {
      this.sectionError = `La prueba pendiente corresponde al conocimiento ${this.pruebaDestinoPendienteId}. Vuelve a ese conocimiento para asociarla.`; return;
    }
    if (this.pruebaCreada) { this.pruebaForm.controls.pruebaId.setValue(this.pruebaCreada.id); this.guardarPrueba(); return; }
    if (this.creacionPruebaIncierta) { this.sectionError = 'El resultado de la creación es incierto. Busca la prueba antes de volver a crear.'; return; }
    if (this.pruebaNuevaForm.invalid) { this.pruebaNuevaForm.markAllAsTouched(); this.sectionError = 'Descripción obligatoria y campos de máximo 500 caracteres.'; return; }
    this.saving = 'pruebas'; this.sectionError = ''; this.pruebaDestinoPendienteId = destino;
    const v = this.pruebaNuevaForm.getRawValue();
    this.injector.get(AdminPruebasApiService).guardar(null, v.descripcion, v.resultadoEsperado)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: creada => {
        this.pruebaCreada = creada; this.saving = null;
        this.pruebasCatalogo = [...this.pruebasCatalogo.filter(p => p.id !== creada.id), creada];
        if (this.conocimientoId !== destino) { this.sectionError = `Prueba ${creada.id} creada para el conocimiento ${destino}; vuelve para asociarla.`; return; }
        this.pruebaForm.controls.pruebaId.setValue(creada.id); this.guardarPrueba();
      }, error: (e: Error & { status?: number }) => { this.saving = null; this.creacionPruebaIncierta = e.status == null || e.status === 0 || e.status >= 500; if (!this.creacionPruebaIncierta) this.pruebaDestinoPendienteId = null; this.sectionError = this.creacionPruebaIncierta ? `${e.message} No se repetirá la creación. Busca la prueba en el catálogo.` : e.message; } });
  }
  recuperarPruebaPendiente(): void {
    if (!this.esAdministrador() || this.saving === 'pruebas') return;
    this.saving = 'pruebas'; const v = this.pruebaNuevaForm.getRawValue();
    this.injector.get(AdminPruebasApiService).listar().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: items => {
        this.saving = null;
        const candidatos = items.filter(p => p.descripcion.trim().toLocaleLowerCase('es') === v.descripcion.trim().toLocaleLowerCase('es')
          && (p.resultadoEsperado || '').trim() === v.resultadoEsperado.trim());
        if (candidatos.length !== 1 || !candidatos[0].activo) { this.sectionError = 'No hay una coincidencia activa única. Revisa el catálogo administrativo antes de crear otra prueba.'; return; }
        this.pruebaCreada = candidatos[0]; this.creacionPruebaIncierta = false; this.sectionError = `Prueba ${this.pruebaCreada.id} encontrada. Puedes reintentar solo la asociación.`;
      }, error: (e: Error) => { this.saving = null; this.sectionError = e.message; },
    });
  }
  comprobarAsociacionPendiente(): void {
    const id = this.pruebaDestinoPendienteId; const creada = this.pruebaCreada;
    if (!this.puedeGestionar() || !id || !creada || this.saving === 'pruebas') return;
    this.saving = 'pruebas';
    this.conocimientosApi.listarPruebas(id, true).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: items => {
        this.saving = null;
        if (this.conocimientoId === id) { this.pruebas = items; this.publicarLista(`pruebas:${id}`, items); }
        if (items.some(p => p.id === creada.id)) {
          this.pruebaCreada = null; this.pruebaDestinoPendienteId = null; this.creacionPruebaIncierta = false;
          this.pruebaNuevaForm.reset({ descripcion: '', resultadoEsperado: '' }); this.formAbierto.pruebas = false;
          this.ok('La prueba ya estaba asociada. No se repitió la creación ni la asociación.');
        } else this.sectionError = `Prueba ${creada.id} creada, todavía no asociada. Puedes reintentar solo la asociación.`;
      }, error: (e: Error) => { this.saving = null; this.sectionError = e.message; },
    });
  }
  materialBusquedaAbierta = false;
  cargandoMaterialesCatalogo = false;
  reutilizandoMaterial = false;
  materialReutilizado = false;
  materialBusquedaError = '';
  materialFiltro = this.fb.nonNullable.control('');
  materialesCatalogo: (MaterialApoyo & { conocimientoId: number; conocimientoTitulo: string })[] = [];
  materialSeleccionado: (MaterialApoyo & { conocimientoId: number; conocimientoTitulo: string }) | null = null;
  get materialesFiltrados() {
    const q = this.materialFiltro.value.trim().toLocaleLowerCase('es');
    return this.materialesCatalogo.filter(m => !q || `${m.nombre} ${m.tipo} ${m.conocimientoTitulo}`.toLocaleLowerCase('es').includes(q));
  }
  abrirBuscarMaterial(): void {
    if (!this.puedeGestionar() || !this.item || this.reutilizandoMaterial) return;
    this.materialBusquedaAbierta = true; this.materialBusquedaError = '';
    if (this.materialReutilizado) this.materialSeleccionado = null;
    this.materialReutilizado = false; this.materialesCatalogo = []; this.cargandoMaterialesCatalogo = true;
    const destino = this.item.id;
    this.conocimientosApi.listarMaterialesCatalogo().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: items => { this.cargandoMaterialesCatalogo = false; if (this.conocimientoId === destino && this.materialBusquedaAbierta) this.materialesCatalogo = items.filter(m => m.conocimientoId !== destino); },
      error: (e: Error) => { this.cargandoMaterialesCatalogo = false; this.materialBusquedaError = e.message; },
    });
  }
  esArchivoMaterialCatalogo(m: MaterialApoyo & { conocimientoId: number }): boolean {
    return (m.tipo === 'PDF' || m.tipo === 'IMAGEN')
      && m.url === `/api/conocimientos/${m.conocimientoId}/materiales/${m.id}/archivo`;
  }
  descargarMaterialCatalogo(m: MaterialApoyo & { conocimientoId: number }): void {
    if (!this.puedeGestionar() || !this.esArchivoMaterialCatalogo(m)) {
      this.materialBusquedaError = 'La descarga no corresponde a un archivo administrado del conocimiento de origen.'; return;
    }
    const url = apiUrl(`/conocimientos/${m.conocimientoId}/materiales/${m.id}/archivo`);
    this.conocimientosApi.descargarMaterial({ ...m, url }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      error: (e: Error) => { this.materialBusquedaError = e.message; },
    });
  }
  seleccionarMaterial(m: (MaterialApoyo & { conocimientoId: number; conocimientoTitulo: string })): void {
    if (!this.reutilizandoMaterial && !this.materialReutilizado) this.materialSeleccionado = m;
  }
  cerrarBuscarMaterial(): void { if (!this.reutilizandoMaterial) this.materialBusquedaAbierta = false; }
  reutilizarMaterialSeleccionado(): void {
    const m = this.materialSeleccionado; const id = this.conocimientoId;
    if (!this.puedeGestionar() || !m || !id || this.reutilizandoMaterial || this.materialReutilizado) return;
    this.reutilizandoMaterial = true; this.materialBusquedaError = '';
    this.conocimientosApi.reutilizarMaterial(id, m.conocimientoId, m.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.materialReutilizado = true; this.recargarMaterialesReutilizados(id); },
      error: (e: Error) => { this.reutilizandoMaterial = false; this.materialBusquedaError = e.message; },
    });
  }
  recargarMaterialesReutilizados(id = this.conocimientoId): void {
    if (!id || !this.materialReutilizado) return;
    this.reutilizandoMaterial = true;
    this.conocimientosApi.listarMateriales(id, true).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: items => { this.reutilizandoMaterial = false; if (this.conocimientoId === id) { this.materiales = items; this.publicarLista(`materiales:${id}`, items); this.ok('Material reutilizado con referencia independiente.'); } this.materialBusquedaAbierta = false; },
      error: (e: Error) => { this.reutilizandoMaterial = false; this.materialBusquedaError = `El material ya se copió. Reintenta solo recargar: ${e.message}`; },
    });
  }
  private currentId: number | null = null;

  editSintomaId: number | null = null;
  editCausaId: number | null = null;
  editSolucionId: number | null = null;
  editMaterialId: number | null = null;
  materialAEliminar: MaterialApoyo | null = null;
  editAsignacionId: number | null = null;
  solucionAsignacionId: number | null = null;

  paso = 1;
  readonly pasosGuia = [
    { id: 1, titulo: 'Observar', detalle: 'Síntomas' },
    { id: 2, titulo: 'Diagnosticar', detalle: 'Causas' },
    { id: 3, titulo: 'Verificar', detalle: 'Pruebas' },
    { id: 4, titulo: 'Resolver', detalle: 'Solución' },
    { id: 5, titulo: 'Apoyo', detalle: 'Material' },
  ] as const;

  formAbierto: Record<'sintomas' | 'causas' | 'pruebas' | 'soluciones' | 'materiales', boolean> = {
    sintomas: false,
    causas: false,
    pruebas: false,
    soluciones: false,
    materiales: false,
  };

  readonly tiposSolucion: TipoSolucion[] = ['PASOS', 'DERIVACION'];
  readonly tiposMaterial: TipoMaterial[] = ['IMAGEN', 'PDF', 'VIDEO', 'ENLACE'];

  sintomaForm = this.fb.nonNullable.group({
    descripcion: ['', Validators.required],
    orden: [1, [Validators.required, Validators.min(1)]],
  });

  causaForm = this.fb.nonNullable.group({
    descripcion: ['', Validators.required],
    orden: [1, [Validators.required, Validators.min(1)]],
  });

  pruebaForm = this.fb.nonNullable.group({
    pruebaId: this.fb.control<number | null>(null, Validators.required),
    orden: [1, [Validators.required, Validators.min(1)]],
  });

  solucionForm = this.fb.nonNullable.group({
    titulo: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(150)]],
    descripcion: ['', Validators.required],
    tipo: this.fb.nonNullable.control<TipoSolucion>('PASOS'),
    orden: [1, [Validators.required, Validators.min(1)]],
  });

  asignacionForm = this.fb.nonNullable.group({
    departamentoId: this.fb.control<number | null>(null),
    responsableId: this.fb.control<number | null>(null),
    principal: [true],
  });

  materialForm = this.fb.nonNullable.group({
    nombre: ['', Validators.required],
    tipo: this.fb.nonNullable.control<TipoMaterial>('PDF'),
    url: [''],
  });
  archivoMaterial: File | null = null;

  get favorito(): boolean {
    return !!this.item && this.favoritosService.tiene(this.item.id);
  }

  get conocimientoId(): number | null {
    return this.item?.id ?? null;
  }

  get mostrandoCarga(): boolean {
    return (
      (this.loading && !this.item) ||
      (this.saving != null && this.materialAEliminar == null) ||
      this.publishing ||
      this.eliminando
    );
  }

  get mensajeCarga(): string {
    if (this.eliminando || this.saving === 'materiales') {
      return 'Eliminando';
    }
    if (this.saving != null || this.publishing) {
      return 'Guardando';
    }
    return 'Cargando';
  }

  ngOnInit(): void {
    this.materialForm.controls.tipo.valueChanges.subscribe((tipo) => {
      if (!admiteUploadLocal(tipo)) {
        this.archivoMaterial = null;
      }
    });

    this.asignacionForm.controls.departamentoId.valueChanges.subscribe((departamentoId) => {
      this.asignacionForm.controls.responsableId.setValue(null, { emitEvent: false });
      this.responsables = [];
      if (departamentoId) {
        this.organizacion.responsablesDeDepartamento(departamentoId).subscribe({
          next: (items) => (this.responsables = items),
          error: (err: Error) => (this.sectionError = err.message),
        });
      }
    });

    this.route.paramMap.subscribe((params) => {
      const id = Number(params.get('id'));
      if (id) {
        this.cargar(id);
      }
    });
  }

  cargar(id: number): void {
    this.reutilizarAbierto = false;
    this.materialBusquedaAbierta = false;
    this.currentId = id;
    this.error = '';
    this.sectionError = '';
    this.sectionSuccess = '';
    this.formAbierto = {
      sintomas: false,
      causas: false,
      pruebas: false,
      soluciones: false,
      materiales: false,
    };

    const cached = this.conocimientosApi.peekDetalle(id);
    if (cached) {
      this.item = cached;
      this.loading = false;
    } else {
      this.loading = true;
      this.item = null;
    }

    this.pintarSeccionesGuardadas(id);

    this.conocimientosApi.obtenerPorId(id).subscribe({
      next: (item) => {
        if (this.currentId !== id) return;
        this.item = item;
        this.loading = false;
      },
      error: (err: Error) => {
        if (this.currentId !== id) return;
        this.error = err.message;
        this.loading = false;
        if (err.message === 'El conocimiento no está disponible.') {
          this.item = null;
        }
      },
    });

    this.cargarSeccionProgresiva(id);
  }

  private pintarSeccionesGuardadas(id: number): void {
    const sintomas = this.conocimientosApi.leerSeccion<typeof this.sintomas>(`sintomas:${id}`);
    const causas = this.conocimientosApi.leerSeccion<typeof this.causas>(`causas:${id}`);
    const pruebas = this.conocimientosApi.leerSeccion<typeof this.pruebas>(`pruebas:${id}`);
    const materiales = this.conocimientosApi.leerSeccion<typeof this.materiales>(`materiales:${id}`);
    if (sintomas) {
      this.sintomas = sintomas;
      this.loadingSintomas = false;
    }
    if (causas) {
      this.causas = causas;
      this.loadingCausas = false;
    }
    if (pruebas) {
      this.pruebas = pruebas;
      this.loadingPruebas = false;
    }
    if (materiales) {
      this.materiales = materiales;
      this.loadingMateriales = false;
    }
  }

  private cargarSeccionProgresiva(id: number): void {
    this.loadingSintomas = this.conocimientosApi.leerSeccion(`sintomas:${id}`) == null;
    this.conocimientosApi.listarSintomas(id).subscribe({
      next: (items) => {
        if (this.currentId !== id) return;
        this.sintomas = items;
        this.sintomaForm.controls.orden.setValue(this.nextOrden(items));
        this.loadingSintomas = false;
      },
      error: (err: Error) => {
        if (this.currentId !== id) return;
        this.sectionError = err.message;
        this.loadingSintomas = false;
      },
    });

    this.loadingCausas = this.conocimientosApi.leerSeccion(`causas:${id}`) == null;
    this.conocimientosApi.listarCausas(id).subscribe({
      next: (items) => {
        if (this.currentId !== id) return;
        this.causas = items;
        this.causaForm.controls.orden.setValue(this.nextOrden(items));
        this.loadingCausas = false;
      },
      error: (err: Error) => {
        if (this.currentId !== id) return;
        this.sectionError = err.message;
        this.loadingCausas = false;
      },
    });

    this.loadingPruebas = this.conocimientosApi.leerSeccion(`pruebas:${id}`) == null;
    this.conocimientosApi.listarPruebas(id).subscribe({
      next: (items) => {
        if (this.currentId !== id) return;
        this.pruebas = items;
        this.pruebaForm.controls.orden.setValue(this.nextOrden(items));
        this.loadingPruebas = false;
      },
      error: (err: Error) => {
        if (this.currentId !== id) return;
        this.sectionError = err.message;
        this.loadingPruebas = false;
      },
    });

    this.loadingSoluciones = this.conocimientosApi.leerSeccion(`soluciones:${id}`) == null;
    this.conocimientosApi.listarSoluciones(id).subscribe({
      next: (items) => {
        if (this.currentId !== id) return;
        this.soluciones = items;
        this.solucionForm.controls.orden.setValue(this.nextOrden(items));
        this.loadingSoluciones = false;
        // Diferir asignaciones para no competir con la primera pintura.
        window.setTimeout(() => {
          if (this.currentId === id) {
            this.cargarAsignaciones(id, items);
          }
        }, 0);
      },
      error: (err: Error) => {
        if (this.currentId !== id) return;
        this.sectionError = err.message;
        this.loadingSoluciones = false;
      },
    });

    this.loadingMateriales = this.conocimientosApi.leerSeccion(`materiales:${id}`) == null;
    this.conocimientosApi.listarMateriales(id).subscribe({
      next: (items) => {
        if (this.currentId !== id) return;
        this.materiales = items;
        this.loadingMateriales = false;
      },
      error: (err: Error) => {
        if (this.currentId !== id) return;
        this.sectionError = err.message;
        this.loadingMateriales = false;
      },
    });
  }

  irPaso(paso: number): void {
    if (paso < 1 || paso > this.pasosGuia.length) {
      return;
    }
    this.paso = paso;
  }

  cantidadPaso(id: number): number {
    if (id === 1) {
      return this.sintomas.length;
    }
    if (id === 2) {
      return this.causas.length;
    }
    if (id === 3) {
      return this.pruebas.length;
    }
    if (id === 4) {
      return this.soluciones.length;
    }
    return this.materiales.length;
  }

  get estaModificando(): boolean {
    return Object.values(this.formAbierto).some(Boolean) || this.solucionAsignacionId != null;
  }

  abrirFormulario(seccion: keyof typeof this.formAbierto): void {
    if (seccion === 'sintomas' && this.editSintomaId == null) {
      this.sintomaForm.controls.orden.setValue(this.nextOrden(this.sintomas));
    }
    this.formAbierto[seccion] = true;

    if (seccion === 'pruebas') {
      this.pruebasCatalogo = []; this.cargandoPruebasCatalogo = true;
      this.organizacion.listarPruebas(true).subscribe({
        next: (items) => { this.pruebasCatalogo = items; this.cargandoPruebasCatalogo = false; },
        error: (err: Error) => { this.sectionError = err.message; this.cargandoPruebasCatalogo = false; },
      });
    }
  }

  private ensureDepartamentos(): void {
    if (this.departamentos.length) return;
    this.organizacion.listarDepartamentos().subscribe({
      next: (items) => (this.departamentos = items),
      error: (err: Error) => (this.sectionError = err.message),
    });
  }

  toggleFavorito(): void {
    if (!this.item) {
      return;
    }
    if (this.favoritosService.tiene(this.item.id)) {
      this.confirmarQuitarFavorito = true;
      return;
    }
    this.favoritosService.toggle(this.item.id, this.item.titulo);
  }

  cancelarQuitarFavorito(): void {
    this.confirmarQuitarFavorito = false;
  }

  aceptarQuitarFavorito(): void {
    if (this.item) {
      this.favoritosService.quitar(this.item.id);
    }
    this.confirmarQuitarFavorito = false;
  }

  cambiarEstado(estado: 'PUBLICADO' | 'BORRADOR'): void {
    if (!this.puedeGestionar() || !this.item || this.item.estado === estado || this.publishing) {
      return;
    }
    if (estado === 'PUBLICADO' && !this.esAdministrador()) {
      return;
    }
    this.publishing = true;
    this.error = '';
    this.conocimientosApi.cambiarEstado(this.item.id, estado).subscribe({
      next: (item) => {
        this.item = item;
        this.publishing = false;
      },
      error: (err: Error) => {
        this.error = err.message;
        this.publishing = false;
      },
    });
  }

  editar(): void {
    if (this.item && this.puedeGestionar()) {
      void this.router.navigate(['/conocimiento', this.item.id, 'editar']);
    }
  }

  pedirEliminar(): void {
    if (!this.item || this.eliminando) {
      return;
    }
    this.confirmarEliminacion = true;
  }

  cancelarEliminar(): void {
    if (this.eliminando) {
      return;
    }
    this.confirmarEliminacion = false;
  }

  confirmarEliminar(): void {
    if (!this.item || this.eliminando) {
      return;
    }
    const id = this.item.id;
    this.eliminando = true;
    this.error = '';
    this.conocimientosApi.eliminar(id).subscribe({
      next: () => {
        this.eliminando = false;
        this.confirmarEliminacion = false;
        void this.router.navigate(['/conocimiento'], { state: { conocimientoEliminado: true } });
      },
      error: (err: Error) => {
        this.eliminando = false;
        this.confirmarEliminacion = false;
        this.error = err.message;
        if (err.message === 'El conocimiento no está disponible.') {
          this.item = null;
        }
      },
    });
  }

  compartir(): void {
    const url = window.location.href;
    if (navigator.clipboard?.writeText) {
      void navigator.clipboard.writeText(url);
    }
    this.copiado = true;
    window.setTimeout(() => (this.copiado = false), 1800);
  }

  cerrarFormulario(seccion: keyof typeof this.formAbierto): void {
    if (this.saving === seccion) return;
    this.formAbierto[seccion] = false;
    if (seccion === 'sintomas') this.cancelarSintoma();
    if (seccion === 'causas') this.cancelarCausa();

    if (seccion === 'soluciones') this.cancelarSolucion();

  }

  guardarSintoma(): void {
    const id = this.conocimientoId;
    if (!id || this.sintomaForm.invalid) {
      this.sintomaForm.markAllAsTouched();
      return;
    }
    const request = this.sintomaForm.getRawValue();
    const previo = this.sintomas;
    const editando = this.editSintomaId;
    const temporal = editando ?? -Date.now();
    this.sintomas =
      editando != null
        ? this.sintomas.map((item) => (item.id === editando ? { ...item, ...request } : item))
        : [...this.sintomas, { id: temporal, descripcion: request.descripcion, orden: request.orden }];
    this.publicarLista(`sintomas:${id}`, this.sintomas);
    this.editSintomaId = null;
    this.formAbierto.sintomas = false;
    this.sintomaForm.reset({ descripcion: '', orden: this.nextOrden(this.sintomas) });
    this.ok(editando != null ? 'Síntoma actualizado.' : 'Síntoma agregado.');
    const req$ =
      editando != null
        ? this.conocimientosApi.modificarSintoma(id, editando, request)
        : this.conocimientosApi.crearSintoma(id, request);
    req$.subscribe({
      next: (creado) => {
        this.sintomas = this.sintomas.map((item) => (item.id === temporal ? creado : item));
        this.publicarLista(`sintomas:${id}`, this.sintomas);
      },
      error: (err: Error) => {
        this.sintomas = previo;
        this.publicarLista(`sintomas:${id}`, previo);
        this.fail(err);
      },
    });
  }

  editarSintoma(item: ItemOrdenado): void {
    this.formAbierto.sintomas = true;
    this.editSintomaId = item.id;
    this.sintomaForm.setValue({ descripcion: item.descripcion, orden: item.orden });
  }

  cancelarSintoma(): void {
    this.editSintomaId = null;
    this.sintomaForm.reset({ descripcion: '', orden: this.nextOrden(this.sintomas) });
  }

  guardarCausa(): void {
    const id = this.conocimientoId;
    if (!id || this.causaForm.invalid) {
      this.causaForm.markAllAsTouched();
      return;
    }
    const request = this.causaForm.getRawValue();
    const previo = this.causas;
    const editando = this.editCausaId;
    const temporal = editando ?? -Date.now();
    this.causas =
      editando != null
        ? this.causas.map((item) => (item.id === editando ? { ...item, ...request } : item))
        : [...this.causas, { id: temporal, descripcion: request.descripcion, orden: request.orden }];
    this.publicarLista(`causas:${id}`, this.causas);
    this.editCausaId = null;
    this.formAbierto.causas = false;
    this.causaForm.reset({ descripcion: '', orden: this.nextOrden(this.causas) });
    this.ok(editando != null ? 'Causa actualizada.' : 'Causa agregada.');
    const req$ =
      editando != null
        ? this.conocimientosApi.modificarCausa(id, editando, request)
        : this.conocimientosApi.crearCausa(id, request);
    req$.subscribe({
      next: (creado) => {
        this.causas = this.causas.map((item) => (item.id === temporal ? creado : item));
        this.publicarLista(`causas:${id}`, this.causas);
      },
      error: (err: Error) => {
        this.causas = previo;
        this.publicarLista(`causas:${id}`, previo);
        this.fail(err);
      },
    });
  }

  editarCausa(item: ItemOrdenado): void {
    this.formAbierto.causas = true;
    this.editCausaId = item.id;
    this.causaForm.setValue({ descripcion: item.descripcion, orden: item.orden });
  }

  cancelarCausa(): void {
    this.editCausaId = null;
    this.causaForm.reset({ descripcion: '', orden: this.nextOrden(this.causas) });
  }

  guardarPrueba(): void {
    const id = this.conocimientoId;
    if (!id || !this.puedeGestionar() || this.saving === 'pruebas') return;
    if (this.pruebaForm.invalid) { this.pruebaForm.markAllAsTouched(); return; }
    const value = this.pruebaForm.getRawValue();
    if (value.pruebaId == null) return;
    if (this.pruebas.some(p => p.id === value.pruebaId)) { this.sectionError = 'Esta prueba ya está asociada al conocimiento.'; return; }
    this.saving = 'pruebas'; this.sectionError = ''; this.sectionSuccess = '';
    this.conocimientosApi.asociarPrueba(id, { pruebaId: value.pruebaId, orden: this.nextOrden(this.pruebas) }).subscribe({
      next: creada => {
        this.saving = null;
        if (this.conocimientoId !== id) return;
        this.pruebas = [...this.pruebas.filter(p => p.id !== creada.id), creada];
        this.publicarLista(`pruebas:${id}`, this.pruebas);
        this.formAbierto.pruebas = false; this.cancelarPrueba();
        if (this.pruebaCreada?.id === creada.id) { this.pruebaCreada = null; this.pruebaDestinoPendienteId = null; this.creacionPruebaIncierta = false; this.pruebaNuevaForm.reset({ descripcion: '', resultadoEsperado: '' }); }
        this.ok('Prueba asociada.');
      },
      error: (err: Error) => { this.saving = null; this.fail(err); },
    });
  }
  cancelarPrueba(): void {
    this.pruebaForm.reset({ pruebaId: null, orden: this.nextOrden(this.pruebas) });
  }

  guardarSolucion(): void {
    const id = this.conocimientoId;
    if (!id || this.solucionForm.invalid || this.saving === 'soluciones') {
      this.solucionForm.markAllAsTouched();
      return;
    }
    const raw = this.solucionForm.getRawValue();
    const request = { ...raw, titulo: raw.titulo.trim(), descripcion: raw.descripcion.trim() };
    const editando = this.editSolucionId;
    this.saving = 'soluciones';
    const req$ = editando != null
      ? this.conocimientosApi.modificarSolucion(id, editando, request)
      : this.conocimientosApi.crearSolucion(id, request);
    req$.subscribe({
      next: (guardada) => {
        this.saving = null;
        this.soluciones = editando != null
          ? this.soluciones.map(item => item.id === editando ? guardada : item)
          : [...this.soluciones, guardada];
        this.publicarLista(`soluciones:${id}`, this.soluciones);
        this.editSolucionId = null;
        this.formAbierto.soluciones = false;
        this.solucionForm.reset({ titulo: '', descripcion: '', tipo: 'PASOS', orden: this.nextOrden(this.soluciones) });
        this.ok(editando != null ? 'Solución actualizada.' : 'Solución agregada.');
      },
      error: (err: Error) => { this.saving = null; this.fail(err); },
    });
  }

  editarSolucion(item: Solucion): void {
    if (!item.activo) return;
    this.formAbierto.soluciones = true;
    this.editSolucionId = item.id;
    this.solucionForm.setValue({
      titulo: item.titulo || '',
      descripcion: item.descripcion,
      tipo: item.tipo,
      orden: item.orden,
    });
  }

  cancelarSolucion(): void {
    this.editSolucionId = null;
    this.solucionForm.reset({ titulo: '', descripcion: '', tipo: 'PASOS', orden: this.nextOrden(this.soluciones) });
  }

  abrirAsignacion(solucionId: number, asignacion?: AsignacionSolucion): void {
    this.ensureDepartamentos();
    const conocimientoId = this.conocimientoId;
    if (conocimientoId && !this.asignacionesPorSolucion[solucionId]) {
      this.reloadAsignaciones(conocimientoId, solucionId);
    }
    this.solucionAsignacionId = solucionId;
    this.editAsignacionId = asignacion?.id ?? null;
    this.asignacionForm.reset({
      departamentoId: asignacion?.departamentoId ?? null,
      responsableId: asignacion?.responsableId ?? null,
      principal: asignacion?.principal ?? true,
    });
    if (asignacion?.departamentoId) {
      this.organizacion.responsablesDeDepartamento(asignacion.departamentoId).subscribe({
        next: (items) => (this.responsables = items),
      });
    }
  }

  cancelarAsignacion(): void {
    this.solucionAsignacionId = null;
    this.editAsignacionId = null;
    this.asignacionForm.reset({ departamentoId: null, responsableId: null, principal: true });
    this.responsables = [];
  }

  guardarAsignacion(): void {
    const conocimientoId = this.conocimientoId;
    const solucionId = this.solucionAsignacionId;
    if (!conocimientoId || !solucionId) {
      return;
    }
    const value = this.asignacionForm.getRawValue();
    if (value.departamentoId == null && value.responsableId == null) {
      this.sectionError = 'Indique al menos un departamento o un responsable.';
      return;
    }
    const request = {
      departamentoId: value.departamentoId,
      responsableId: value.responsableId,
      principal: value.principal,
    };
    const clave = `asignaciones:${conocimientoId}:${solucionId}`;
    const previo = this.asignacionesDe(solucionId);
    const editando = this.editAsignacionId;
    const temporal = editando ?? -Date.now();
    const departamento = this.departamentos.find((item) => item.id === value.departamentoId);
    const responsable = this.responsables.find((item) => item.id === value.responsableId);
    const fila: AsignacionSolucion = {
      id: temporal,
      departamentoId: value.departamentoId,
      departamentoNombre: departamento?.nombre ?? null,
      responsableId: value.responsableId,
      responsableNombre: responsable?.nombre ?? null,
      principal: value.principal,
    };
    const siguiente =
      editando != null ? previo.map((item) => (item.id === editando ? { ...item, ...fila, id: editando } : item)) : [...previo, fila];
    this.asignacionesPorSolucion = { ...this.asignacionesPorSolucion, [solucionId]: siguiente };
    this.publicarLista(clave, siguiente);
    this.cancelarAsignacion();
    this.ok('Asignación guardada.');
    const req$ =
      editando != null
        ? this.conocimientosApi.modificarAsignacion(conocimientoId, solucionId, editando, request)
        : this.conocimientosApi.crearAsignacion(conocimientoId, solucionId, request);
    req$.subscribe({
      next: (creada) => {
        const actual = this.asignacionesDe(solucionId).map((item) => (item.id === temporal ? creada : item));
        this.asignacionesPorSolucion = { ...this.asignacionesPorSolucion, [solucionId]: actual };
        this.publicarLista(clave, actual);
      },
      error: (err: Error) => {
        this.asignacionesPorSolucion = { ...this.asignacionesPorSolucion, [solucionId]: previo };
        this.publicarLista(clave, previo);
        this.fail(err);
      },
    });
  }

  get muestraUrlMaterial(): boolean {
    return muestraUrlMaterial(this.materialForm.controls.tipo.value);
  }

  get admiteAdjuntoMaterial(): boolean {
    return this.editMaterialId == null && admiteUploadLocal(this.materialForm.controls.tipo.value);
  }

  get acceptMaterial(): string {
    return acceptArchivoMaterial(this.materialForm.controls.tipo.value);
  }

  get pistaAdjuntoMaterial(): string {
    const tipo = this.materialForm.controls.tipo.value;
    if (tipo === 'PDF') {
      return 'PDF · máximo 10 MiB';
    }
    if (tipo === 'IMAGEN') {
      return 'PNG, JPEG o WEBP · máximo 10 MiB';
    }
    return 'MP4, WEBM o MOV · máximo 10 MiB';
  }

  elegirArchivoMaterial(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.archivoMaterial = input.files?.[0] ?? null;
  }

  guardarMaterialDesdeFormulario(): void {
    const valor = this.materialForm.getRawValue();
    const origen = origenDesdeAdjunto(valor.tipo, this.archivoMaterial);
    const error = validarMaterialFormulario({
      nombre: valor.nombre,
      tipo: valor.tipo,
      origen,
      url: valor.url,
      archivo: this.archivoMaterial,
    });
    if (error) {
      this.sectionError = error;
      this.materialForm.markAllAsTouched();
      return;
    }
    this.guardarMaterial({
      nombre: valor.nombre,
      tipo: valor.tipo,
      origen,
      url: valor.url,
      archivo: this.archivoMaterial,
    });
  }

  private guardarMaterial(valor: {
    nombre: string;
    tipo: TipoMaterial;
    origen: OrigenMaterial;
    url: string;
    archivo: File | null;
  }): void {
    const id = this.conocimientoId;
    if (!id) {
      return;
    }
    if (!this.puedeGestionar() || this.saving === 'materiales') return;
    this.sectionError = ''; this.sectionSuccess = ''; this.saving = 'materiales';
    const editando = this.editMaterialId;
    const req$ =
      valor.origen === 'archivo' && valor.archivo
        ? this.conocimientosApi.crearMaterialArchivo(id, valor.nombre, valor.tipo, valor.archivo)
        : editando != null
          ? this.conocimientosApi.modificarMaterial(id, editando, {
              nombre: valor.nombre.trim(),
              tipo: valor.tipo,
              url: valor.url.trim(),
            })
          : this.conocimientosApi.crearMaterial(id, {
              nombre: valor.nombre.trim(),
              tipo: valor.tipo,
              url: valor.url.trim(),
            });
    req$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: creado => {
        this.saving = null;
        if (this.conocimientoId !== id) return;
        this.materiales = editando != null ? this.materiales.map(m => m.id === editando ? creado : m) : [...this.materiales, creado];
        this.publicarLista(`materiales:${id}`, this.materiales);
        this.resetMaterialFormulario(); this.formAbierto.materiales = false;
        this.ok(editando != null ? 'Material actualizado.' : 'Material agregado.');
      }, error: (err: Error) => { this.saving = null; this.fail(err); },
    });
  }

  esMaterialLocal(item: MaterialApoyo): boolean {
    return item.url === `/api/conocimientos/${this.conocimientoId}/materiales/${item.id}/archivo`;
  }

  descargarMaterial(item: MaterialApoyo): void {
    if (!this.esMaterialLocal(item)) { this.fail(new Error('La descarga no corresponde al conocimiento actual.')); return; }
    const url = apiUrl(`/conocimientos/${this.conocimientoId}/materiales/${item.id}/archivo`);
    this.conocimientosApi.descargarMaterial({ ...item, url }).subscribe({
      error: (err: Error) => this.fail(err),
    });
  }

  editarMaterial(item: MaterialApoyo): void {
    if (this.esMaterialLocal(item)) {
      return;
    }
    this.formAbierto.materiales = true;
    this.editMaterialId = item.id;
    this.archivoMaterial = null;
    this.materialForm.reset({
      nombre: item.nombre,
      tipo: item.tipo,
      url: item.url,
    });
  }

  tipoMaterialLabel(tipo: TipoMaterial): string {
    switch (tipo) {
      case 'IMAGEN':
        return 'Imagen';
      case 'PDF':
        return 'PDF';
      case 'VIDEO':
        return 'Video';
      default:
        return 'Enlace';
    }
  }

  tipoMaterialShort(tipo: TipoMaterial): string {
    switch (tipo) {
      case 'IMAGEN':
        return 'IMG';
      case 'PDF':
        return 'PDF';
      case 'VIDEO':
        return 'VID';
      default:
        return 'URL';
    }
  }

  tipoMaterialClass(tipo: TipoMaterial): string {
    return `tipo-${tipo.toLowerCase()}`;
  }

  cancelarMaterial(): void {
    this.resetMaterialFormulario();
  }

  pedirEliminarMaterial(item: MaterialApoyo): void {
    this.sectionError = '';
    this.materialAEliminar = item;
  }

  cancelarEliminarMaterial(): void {
    if (this.saving === 'materiales') {
      return;
    }
    this.materialAEliminar = null;
  }

  confirmarEliminarMaterial(): void {
    const id = this.conocimientoId;
    const material = this.materialAEliminar;
    if (!id || !material || this.saving === 'materiales') {
      return;
    }
    const previo = this.materiales;
    this.materialAEliminar = null;
    this.sectionError = '';
    this.materiales = this.materiales.filter((item) => item.id !== material.id);
    this.publicarLista(`materiales:${id}`, this.materiales);
    this.ok('Material eliminado.');
    this.conocimientosApi.eliminarMaterial(id, material.id).subscribe({
      error: (err: Error) => {
        this.materiales = previo;
        this.publicarLista(`materiales:${id}`, previo);
        this.fail(err);
      },
    });
  }

  private resetMaterialFormulario(): void {
    this.editMaterialId = null;
    this.archivoMaterial = null;
    this.materialForm.reset({ nombre: '', tipo: 'PDF', url: '' });
  }

  asignacionesDe(solucionId: number): AsignacionSolucion[] {
    return this.asignacionesPorSolucion[solucionId] ?? [];
  }

  private cargarAsignaciones(conocimientoId: number, soluciones: Solucion[]): void {
    this.asignacionesPorSolucion = {};
    for (const solucion of soluciones) {
      this.conocimientosApi.listarAsignaciones(conocimientoId, solucion.id).subscribe({
        next: (items) => {
          if (this.currentId !== conocimientoId) return;
          this.asignacionesPorSolucion = {
            ...this.asignacionesPorSolucion,
            [solucion.id]: items,
          };
        },
      });
    }
  }

  private reloadSintomas(id: number): void {
    this.conocimientosApi.listarSintomas(id, true).subscribe({
      next: (items) => {
        this.sintomas = items;
        this.ok('Síntoma guardado.');
      },
      error: (err: Error) => this.fail(err),
    });
  }

  private reloadCausas(id: number): void {
    this.conocimientosApi.listarCausas(id, true).subscribe({
      next: (items) => {
        this.causas = items;
        this.ok('Causa guardada.');
      },
      error: (err: Error) => this.fail(err),
    });
  }

  private reloadPruebas(id: number): void {
    this.conocimientosApi.listarPruebas(id, true).subscribe({
      next: (items) => {
        this.pruebas = items;
        this.ok('Prueba asociada guardada.');
      },
      error: (err: Error) => this.fail(err),
    });
  }

  private reloadSoluciones(id: number): void {
    this.conocimientosApi.listarSoluciones(id, true).subscribe({
      next: (items) => {
        this.soluciones = items;
        this.cargarAsignaciones(id, items);
        this.ok('Solución guardada.');
      },
      error: (err: Error) => this.fail(err),
    });
  }

  private reloadAsignaciones(conocimientoId: number, solucionId: number): void {
    this.conocimientosApi.listarAsignaciones(conocimientoId, solucionId, true).subscribe({
      next: (items) => {
        this.asignacionesPorSolucion = { ...this.asignacionesPorSolucion, [solucionId]: items };
      },
      error: (err: Error) => this.fail(err),
    });
  }

  private reloadMateriales(id: number, mensaje = 'Material guardado.'): void {
    this.conocimientosApi.listarMateriales(id, true).subscribe({
      next: (items) => {
        this.materiales = items;
        this.ok(mensaje);
      },
      error: (err: Error) => this.fail(err),
    });
  }

  private publicarLista(clave: string, items: unknown[]): void {
    this.conocimientosApi.recordarSeccion(clave, items);
  }


  private nextOrden(items: { orden: number }[]): number {
    return items.reduce((max, item) => Math.max(max, item.orden || 0), 0) + 1;
  }

  private ok(message: string): void {
    this.saving = null;
    this.sectionError = '';
    this.sectionSuccess = message;
  }

  private fail(err: Error): void {
    this.saving = null;
    this.sectionSuccess = '';
    this.sectionError = err.message;
  }
}
