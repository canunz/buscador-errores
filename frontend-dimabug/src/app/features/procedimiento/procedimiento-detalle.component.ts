import { Component, OnInit, ViewChild, inject } from '@angular/core';
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

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
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
    this.pasoAbierto = true;
    this.editPasoId = paso?.id ?? null;
    this.orden = paso?.orden ?? this.siguienteOrden();
    this.instruccion = paso?.instruccion ?? '';
    this.esCritico = paso?.esCritico ?? false;
    this.error = '';
  }

  cerrarPaso(): void {
    if (this.guardandoPaso) return;
    this.pasoAbierto = false;
    this.editPasoId = null;
  }

  guardarPaso(): void {
    if (!this.item || this.guardandoPaso || !this.instruccion.trim() || this.orden < 1) {
      this.error = 'El orden debe ser mayor que cero y la instrucción no puede quedar vacía.';
      return;
    }
    const request = {
      orden: Number(this.orden),
      instruccion: this.instruccion.trim(),
      esCritico: this.esCritico,
    };
    this.guardandoPaso = true;
    this.error = '';
    const esNuevo = !this.editPasoId;
    const llamada = this.editPasoId
      ? this.procedimientos.actualizarPaso(this.item.id, this.editPasoId, request)
      : this.procedimientos.crearPaso(this.item.id, request);
    llamada.subscribe({
      next: () => {
        this.guardandoPaso = false;
        this.cerrarPaso();
        this.recargarPasos(esNuevo);
      },
      error: (err: Error) => {
        this.guardandoPaso = false;
        this.error = err.message;
      },
    });
  }

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
    if (!this.item) return;
    const id = this.item.id;
    this.procedimientos.listarPasos(id).subscribe({
      next: (pasos) => {
        this.aplicarPasos(id, pasos, false);
        if (irAlUltimo) this.pasoIndex = Math.max(0, pasos.length - 1);
      },
      error: (err: Error) => (this.error = err.message),
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
