import { Component, HostListener, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  MaterialPaso,
  PasoProcedimiento,
  Procedimiento,
  TipoMaterialProcedimiento,
} from '../../core/models/procedimiento.model';
import { AuthService } from '../../core/services/auth.service';
import { EjecucionService } from '../../core/services/ejecucion.service';
import { ProcedimientoService } from '../../core/services/procedimiento.service';
import { esUrlDescargaAutenticada } from '../../core/material/material-archivo';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';
import {
  MaterialApoyoFormComponent,
  MaterialApoyoFormValue,
} from '../../shared/ui/material-apoyo-form.component';

@Component({
  selector: 'app-procedimiento-detalle',
  standalone: true,
  imports: [FormsModule, RouterLink, LoadingModalComponent, MaterialApoyoFormComponent],
  templateUrl: './procedimiento-detalle.component.html',
  styleUrl: './procedimiento-detalle.component.css',
})
export class ProcedimientoDetalleComponent implements OnInit {
  private readonly procedimientos = inject(ProcedimientoService);
  private readonly ejecuciones = inject(EjecucionService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  @ViewChild(MaterialApoyoFormComponent) materialEditor?: MaterialApoyoFormComponent;

  readonly esAdministrador = this.auth.isAdmin;
  readonly puedeGestionar = this.auth.isStaff;
  readonly tipos: TipoMaterialProcedimiento[] = ['IMAGEN', 'PDF', 'VIDEO', 'ENLACE'];

  item: Procedimiento | null = null;
  pasos: PasoProcedimiento[] = [];
  materiales: Record<number, MaterialPaso[]> = {};
  pasoIndex = 0;
  cargandoPasos = false;
  cargaInicial = true;
  loading = true;
  error = '';
  iniciando = false;
  publicando = false;
  guardandoPaso = false;
  guardandoMaterial = false;
  pasoAbierto = false;
  editPasoId: number | null = null;
  orden = 1;
  instruccion = '';
  esCritico = false;
  materialPasoId: number | null = null;
  editMaterialId: number | null = null;
  materialNombre = '';
  materialTipo: TipoMaterialProcedimiento = 'PDF';
  materialUrl = '';
  materialAEliminar: { pasoId: number; material: MaterialPaso } | null = null;
  eliminandoMaterial = false;
  aviso = '';
  avisoPublicacion = '';
  recargaPasosPendiente = false;
  pasoPorVerificar = false;
  verificandoPaso = false;
  private pasoInicial = { orden: 1, instruccion: '', esCritico: false };
  private intentoPaso: { id: number | null; orden: number; instruccion: string; esCritico: boolean } | null = null;
  operacionEnCurso(): boolean { return this.guardandoPaso || this.verificandoPaso || this.guardandoMaterial || this.publicando || this.eliminandoMaterial; }
  tieneCambiosPendientes(): boolean {
    return this.pasoPorVerificar || (this.pasoAbierto && (this.orden !== this.pasoInicial.orden || this.instruccion !== this.pasoInicial.instruccion || this.esCritico !== this.pasoInicial.esCritico));
  }
  @HostListener('window:beforeunload', ['$event']) antesDeSalir(event: BeforeUnloadEvent): void {
    if (this.operacionEnCurso() || this.tieneCambiosPendientes()) { event.preventDefault(); event.returnValue = ''; }
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isInteger(id) || id < 1 || id > 2147483647) {
      this.error = 'El procedimiento o recurso solicitado no está disponible.';
      this.loading = false;
      this.cargaInicial = false;
      return;
    }
    this.cargar(id);
  }

  materialesDe(pasoId: number): MaterialPaso[] {
    return this.materiales[pasoId] ?? [];
  }

  etiquetaTipo(tipo: TipoMaterialProcedimiento): string {
    if (tipo === 'IMAGEN') return 'Imagen';
    if (tipo === 'PDF') return 'PDF';
    if (tipo === 'VIDEO') return 'Video';
    return 'Enlace';
  }

  tipoCorto(tipo: TipoMaterialProcedimiento): string {
    if (tipo === 'IMAGEN') return 'IMG';
    if (tipo === 'PDF') return 'PDF';
    if (tipo === 'VIDEO') return 'VID';
    return 'URL';
  }

  tipoClase(tipo: TipoMaterialProcedimiento): string {
    return `tipo-${tipo.toLowerCase()}`;
  }

  get pasoActual(): PasoProcedimiento | null {
    return this.pasos[this.pasoIndex] ?? null;
  }

  irPaso(indice: number): void {
    if (indice < 0 || indice >= this.pasos.length) return;
    this.pasoIndex = indice;
  }

  get pasosCriticos(): number {
    return this.pasos.filter((paso) => paso.esCritico).length;
  }

  get totalMateriales(): number {
    return Object.values(this.materiales).reduce((total, items) => total + items.length, 0);
  }

  abrirPaso(paso?: PasoProcedimiento): void {
    if (!this.puedeGestionar() || this.item?.estado !== 'BORRADOR' || this.operacionEnCurso()) return;
    if (this.tieneCambiosPendientes()) { this.error = 'Conserve o descarte el paso pendiente antes de abrir otro.'; return; }
    this.pasoAbierto = true;
    this.editPasoId = paso?.id ?? null;
    this.orden = paso?.orden ?? this.siguienteOrden();
    this.instruccion = paso?.instruccion ?? '';
    this.esCritico = paso?.esCritico ?? false;
    this.pasoInicial = { orden: this.orden, instruccion: this.instruccion, esCritico: this.esCritico };
    this.intentoPaso = null;
    this.error = '';
  }

  cerrarPaso(): void {
    if (this.guardandoPaso || this.verificandoPaso) return;
    if (this.tieneCambiosPendientes() && !window.confirm('¿Descartar los datos pendientes del paso? Los registros confirmados del procedimiento se conservan.')) return;
    this.pasoAbierto = false; this.editPasoId = null; this.pasoPorVerificar = false; this.intentoPaso = null;
  }
  guardarPaso(): void {
    if (this.guardandoPaso || this.verificandoPaso) return;
    if (!this.puedeGestionar() || this.item?.estado !== 'BORRADOR') { this.error = 'Solo se pueden editar pasos de un procedimiento en BORRADOR.'; return; }
    if (!Number.isInteger(this.item.id) || this.item.id < 1 || (this.editPasoId != null && (!Number.isInteger(this.editPasoId) || this.editPasoId < 1))) { this.error = 'El identificador del procedimiento o paso no es válido.'; return; }
    if (this.pasoPorVerificar) { this.verificarPasoPendiente(); return; }
    if (!this.instruccion.trim()) { this.error = 'La instrucción del paso es obligatoria.'; return; }
    if (!Number.isInteger(Number(this.orden)) || Number(this.orden) < 1 || Number(this.orden) > 2147483647) { this.error = 'El orden debe ser un entero positivo válido para el Backend.'; return; }
    const request = { orden: Number(this.orden), instruccion: this.instruccion.trim(), esCritico: this.esCritico };
    this.intentoPaso = { ...request, id: this.editPasoId }; this.aviso = ''; this.guardandoPaso = true; this.error = '';
    const llamada = this.editPasoId ? this.procedimientos.actualizarPaso(this.item.id, this.editPasoId, request) : this.procedimientos.crearPaso(this.item.id, request);
    llamada.subscribe({
      next: paso => { this.guardandoPaso = false; this.confirmarPaso(paso); },
      error: (e: Error & { status?: number }) => {
        this.guardandoPaso = false;
        this.pasoPorVerificar = e.status == null || e.status === 0 || e.status >= 500 || e.status === 409;
        this.error = `Procedimiento ${this.item!.id} conservado; ${this.pasos.length} pasos confirmados en esta pantalla. El paso de orden ${request.orden} no quedó confirmado por esta solicitud; sus datos permanecen en el formulario. ${e.message}`;
      },
    });
  }
  private confirmarPaso(paso: PasoProcedimiento): void {
    const esNuevo = this.intentoPaso?.id == null;
    this.pasos = [...this.pasos.filter(p => p.id !== paso.id), paso].sort((a, b) => a.orden - b.orden);
    this.aviso = `Paso guardado: ID ${paso.id}, orden ${paso.orden}. El procedimiento ${this.item!.id} conserva sus registros confirmados.`;
    this.error = ''; this.pasoPorVerificar = false; this.intentoPaso = null; this.pasoAbierto = false; this.editPasoId = null;
    this.recargarPasos(esNuevo);
  }
  verificarPasoPendiente(): void {
    if (!this.item || !this.intentoPaso || this.verificandoPaso || this.guardandoPaso) return;
    const intento = this.intentoPaso; this.verificandoPaso = true;
    this.procedimientos.listarPasos(this.item.id, true).subscribe({
      next: pasos => {
        this.verificandoPaso = false; this.pasos = pasos;
        const encontrado = pasos.find(p => intento.id == null ? p.orden === intento.orden : p.id === intento.id);
        if (encontrado && encontrado.orden === intento.orden && encontrado.instruccion === intento.instruccion && encontrado.esCritico === intento.esCritico) {
          this.pasoPorVerificar = false; this.intentoPaso = null; this.pasoAbierto = false; this.editPasoId = null;
          this.error = ''; this.aviso = `El servidor contiene el paso ID ${encontrado.id}, orden ${encontrado.orden}, con los datos enviados. No se reenviará la operación.`;
          this.recargaPasosPendiente = false;
        } else if (encontrado && intento.id == null) {
          this.error = 'El orden ya está ocupado por otro paso. Sus datos se conservan; revise el registro y corrija el orden antes de guardar.';
          this.pasoPorVerificar = false; this.intentoPaso = null;
        } else {
          this.error = 'La consulta no confirmó los datos enviados. Se conservó el formulario. Revise los datos y pulse Guardar para reintentar sobre este mismo BORRADOR.';
          this.pasoPorVerificar = false; this.intentoPaso = null;
        }
      },
      error: (e: Error) => { this.verificandoPaso = false; this.error = `No fue posible verificar el paso. No se reenviará mientras no se consulte el servidor. ${e.message}`; },
    });
  }
  reintentarRecargaPasos(): void { this.recargarPasos(); }
  esMaterialLocal(material: MaterialPaso): boolean {
    return esUrlDescargaAutenticada(material.url);
  }

  descargarMaterial(material: MaterialPaso): void {
    this.procedimientos.descargarMaterial(material).subscribe({
      error: (err: Error) => (this.error = err.message),
    });
  }

  abrirMaterial(pasoId: number, material?: MaterialPaso): void {
    this.materialPasoId = pasoId;
    this.editMaterialId = material?.id ?? null;
    this.materialNombre = material?.nombre ?? '';
    this.materialTipo = material?.tipo ?? 'PDF';
    this.materialUrl = material?.url ?? '';
    this.error = '';
    queueMicrotask(() =>
      this.materialEditor?.reset({
        origen: 'enlace',
        nombre: this.materialNombre,
        tipo: this.materialTipo,
        url: this.materialUrl,
      }),
    );
  }

  pedirEliminarMaterial(pasoId: number, material: MaterialPaso): void {
    this.error = '';
    this.aviso = '';
    this.materialAEliminar = { pasoId, material };
  }

  cancelarEliminarMaterial(): void {
    if (this.eliminandoMaterial) {
      return;
    }
    this.materialAEliminar = null;
  }

  confirmarEliminarMaterial(): void {
    if (!this.item || !this.materialAEliminar || this.eliminandoMaterial) {
      return;
    }
    const { pasoId, material } = this.materialAEliminar;
    this.eliminandoMaterial = true;
    this.materialAEliminar = null;
    this.error = '';
    this.procedimientos.eliminarMaterial(this.item.id, pasoId, material.id).subscribe({
      next: () => {
        this.materiales = {
          ...this.materiales,
          [pasoId]: this.materialesDe(pasoId).filter((item) => item.id !== material.id),
        };
        this.eliminandoMaterial = false;
        this.aviso = 'Material eliminado.';
      },
      error: (err: Error) => {
        this.eliminandoMaterial = false;
        this.error = err.message;
      },
    });
  }

  cerrarMaterial(): void {
    if (this.guardandoMaterial) return;
    this.materialPasoId = null;
    this.editMaterialId = null;
  }

  guardarMaterial(valor: MaterialApoyoFormValue): void {
    if (!this.item || this.materialPasoId == null || this.guardandoMaterial) return;
    const procedimientoId = this.item.id;
    const pasoId = this.materialPasoId;
    const editId = this.editMaterialId;
    const reemplazado = valor.origen === 'archivo' ? editId : null;
    const previo = this.materialesDe(pasoId);
    const tempId = reemplazado != null || editId == null ? -Date.now() : editId;
    const provisional: MaterialPaso = {
      id: tempId,
      nombre: valor.nombre.trim(),
      tipo: valor.tipo,
      url: valor.origen === 'archivo' ? '' : valor.url.trim(),
    };
    const lista =
      reemplazado != null
        ? [...previo.filter((item) => item.id !== reemplazado), provisional]
        : editId != null
          ? previo.map((item) => (item.id === editId ? provisional : item))
          : [...previo, provisional];
    this.ponerMateriales(pasoId, lista);
    this.materialPasoId = null;
    this.editMaterialId = null;
    this.error = '';

    const llamada =
      valor.origen === 'archivo' && valor.archivo
        ? this.procedimientos.crearMaterialArchivo(procedimientoId, pasoId, valor.nombre, valor.tipo, valor.archivo)
        : editId != null && reemplazado == null
          ? this.procedimientos.actualizarMaterial(procedimientoId, pasoId, editId, {
              nombre: valor.nombre.trim(),
              tipo: valor.tipo,
              url: valor.url.trim(),
            })
          : this.procedimientos.crearMaterial(procedimientoId, pasoId, {
              nombre: valor.nombre.trim(),
              tipo: valor.tipo,
              url: valor.url.trim(),
            });

    llamada.subscribe({
      next: (creado) => {
        const actual = this.materialesDe(pasoId).map((item) => (item.id === tempId ? creado : item));
        this.ponerMateriales(pasoId, actual);
        if (reemplazado == null) {
          return;
        }
        this.procedimientos.eliminarMaterial(procedimientoId, pasoId, reemplazado).subscribe({
          error: (err: Error) => {
            this.error = `El archivo nuevo se subió, pero no se pudo quitar el anterior: ${err.message}`;
          },
        });
      },
      error: (err: Error) => {
        this.ponerMateriales(pasoId, previo);
        this.error = err.message;
      },
    });
  }

  publicar(): void {
    if (!this.item || !this.esAdministrador() || this.item.estado !== 'BORRADOR' || this.publicando) return;
    if (this.operacionEnCurso() || this.tieneCambiosPendientes()) { this.error = 'Guarde o descarte el paso pendiente antes de publicar.'; return; }
    this.avisoPublicacion = this.pasos.length === 0 ? 'Se permite publicar sin pasos. Para ejecutarlo será necesario al menos uno; un PUBLICADO no admite edición de pasos.' : '';
    this.publicando = true;
    this.error = '';
    this.procedimientos.cambiarEstado(this.item.id, 'PUBLICADO').subscribe({
      next: (item) => {
        this.item = item;
        this.publicando = false;
      },
      error: (err: Error) => {
        this.publicando = false;
        this.error = err.message;
      },
    });
  }

  iniciar(): void {
    if (!this.item || this.item.estado !== 'PUBLICADO' || this.iniciando) return;
    this.iniciando = true;
    this.error = '';
    this.ejecuciones.iniciar(this.item.id).subscribe({
      next: (ejecucion) => void this.router.navigate(['/ejecuciones', ejecucion.id]),
      error: (err: Error) => {
        this.iniciando = false;
        this.error = err.message;
      },
    });
  }

  private cargar(id: number): void {
    const enCache = this.procedimientos.procedimientoEnCache(id);
    if (enCache) {
      this.mostrarProcedimiento(enCache);
    }
    const pasosCache = this.procedimientos.pasosEnCache(id);
    if (pasosCache) {
      this.aplicarPasos(id, pasosCache, true);
    }
    this.loading = !enCache;
    this.cargandoPasos = !pasosCache;
    this.cargaInicial = !enCache;

    let procListo = !!enCache;
    let pasosListos = !!pasosCache;
    const publicar = () => {
      if (procListo && pasosListos) {
        this.cargaInicial = false;
      }
    };

    this.procedimientos.obtenerPorId(id).subscribe({
      next: (item) => {
        this.mostrarProcedimiento(item);
        this.loading = false;
        procListo = true;
        publicar();
      },
      error: (err: Error) => {
        this.error = err.message;
        this.loading = false;
        this.cargaInicial = false;
      },
    });
    this.procedimientos.listarPasos(id).subscribe({
      next: (pasos) => {
        this.aplicarPasos(id, pasos, false);
        this.cargandoPasos = false;
        pasosListos = true;
        publicar();
      },
      error: (err: Error) => {
        this.error = err.message;
        this.cargandoPasos = false;
        this.cargaInicial = false;
      },
    });
  }

  private mostrarProcedimiento(item: Procedimiento): void {
    if (!this.puedeGestionar() && item.estado !== 'PUBLICADO') {
      this.item = null;
      this.error = 'Este procedimiento no está publicado.';
      return;
    }
    this.item = item;
  }

  private aplicarPasos(
    procedimientoId: number,
    pasos: PasoProcedimiento[],
    soloCache: boolean,
    alCargarMaterial?: () => void,
  ): void {
    this.pasos = pasos;
    if (this.pasoIndex >= pasos.length) {
      this.pasoIndex = Math.max(0, pasos.length - 1);
    }
    const materiales: Record<number, MaterialPaso[]> = {};
    for (const paso of pasos) {
      const cache = this.procedimientos.materialesEnCache(paso.id);
      materiales[paso.id] = cache ?? this.materiales[paso.id] ?? [];
    }
    this.materiales = materiales;
    if (soloCache) return;
    for (const paso of pasos) {
      const marca = this.procedimientos.marcaMateriales(paso.id);
      this.procedimientos.listarMateriales(procedimientoId, paso.id).subscribe({
        next: (items) => {
          if (this.procedimientos.marcaMateriales(paso.id) !== marca) {
            alCargarMaterial?.();
            return;
          }
          this.materiales = { ...this.materiales, [paso.id]: items };
          alCargarMaterial?.();
        },
        error: (err: Error) => {
          this.error = err.message;
          alCargarMaterial?.();
        },
      });
    }
  }

  private recargarPasos(irAlUltimo = false): void {
    if (!this.item || this.cargandoPasos) return;
    const id = this.item.id; this.cargandoPasos = true;
    this.procedimientos.listarPasos(id, true).subscribe({
      next: pasos => { this.cargandoPasos = false; this.recargaPasosPendiente = false; this.error = ''; this.aplicarPasos(id, pasos, false); if (irAlUltimo) this.pasoIndex = Math.max(0, pasos.length - 1); },
      error: (e: Error) => { this.cargandoPasos = false; this.recargaPasosPendiente = true; this.error = `No se actualizó el listado. Los pasos confirmados se conservan y no deben volver a crearse. Reintente solo la consulta. ${e.message}`; },
    });
  }
  private ponerMateriales(pasoId: number, items: MaterialPaso[]): void {
    this.materiales = { ...this.materiales, [pasoId]: items };
    this.procedimientos.fijarMateriales(pasoId, items);
  }

  private cargarMateriales(pasoId: number): void {
    if (!this.item) return;
    const marca = this.procedimientos.marcaMateriales(pasoId);
    this.procedimientos.listarMateriales(this.item.id, pasoId).subscribe({
      next: (items) => {
        if (this.procedimientos.marcaMateriales(pasoId) !== marca) {
          return;
        }
        this.materiales = { ...this.materiales, [pasoId]: items };
      },
      error: (err: Error) => (this.error = err.message),
    });
  }

  private siguienteOrden(): number {
    return this.pasos.reduce((max, paso) => Math.max(max, paso.orden), 0) + 1;
  }
}
