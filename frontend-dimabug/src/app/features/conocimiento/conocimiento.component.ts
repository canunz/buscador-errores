import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { CatalogoRef, Conocimiento, ResultadoBusquedaConocimiento } from '../../core/models/conocimiento.model';
import { AuthService } from '../../core/services/auth.service';
import { ClasificacionService } from '../../core/services/clasificacion.service';
import { ConocimientoService } from '../../core/services/conocimiento.service';
import { FavoritosService } from '../../core/services/favoritos.service';
import { AyudaFranjaComponent } from '../../shared/ui/ayuda-franja.component';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';

type TipoIcono = 'impresora' | 'pantalla' | 'red' | 'servidor' | 'documento';

@Component({
  selector: 'app-conocimiento',
  standalone: true,
  imports: [FormsModule, RouterLink, LoadingModalComponent, DatePipe, NgTemplateOutlet, AyudaFranjaComponent],
  templateUrl: './conocimiento.component.html',
  styleUrl: './conocimiento.component.css',
})
export class ConocimientoComponent implements OnInit, OnDestroy {
  private readonly conocimientosApi = inject(ConocimientoService);
  private readonly clasificacion = inject(ClasificacionService);
  private readonly favoritosService = inject(FavoritosService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private querySub?: Subscription;
  private busquedaSub?: Subscription;
  private listaSub?: Subscription;

  readonly esAdministrador = this.auth.isAdmin;
  readonly puedeGestionar = this.auth.isStaff;

  resultados: ResultadoBusquedaConocimiento[] = [];
  borradores: Conocimiento[] = [];
  private catalogo: Conocimiento[] = [];
  hardwares: CatalogoRef[] = [];
  sistemas: CatalogoRef[] = [];
  modulos: CatalogoRef[] = [];
  frecuencias: CatalogoRef[] = [];
  texto = '';
  hardwareId: number | null = null;
  sistemaId: number | null = null;
  moduloId: number | null = null;
  frecuenciaId: number | null = null;
  pestana: 'publicados' | 'borradores' = 'publicados';
  loading = false;
  buscado = false;
  private debounceBusqueda: ReturnType<typeof setTimeout> | null = null;
  error = '';
  aviso = '';
  pendienteEliminar: { id: number; titulo: string } | null = null;
  pendienteQuitarFavorito: { id: number; titulo: string } | null = null;
  eliminando = false;
  publicandoId: number | null = null;
  private detalles = new Map<number, Conocimiento>();

  ngOnInit(): void {
    const state = history.state as { conocimientoEliminado?: boolean } | null;
    if (state?.conocimientoEliminado) {
      this.aviso = 'El conocimiento dejó de estar disponible para consulta y búsqueda.';
      history.replaceState({ ...state, conocimientoEliminado: false }, '');
    }
    this.texto = this.route.snapshot.queryParamMap.get('q') || '';
    this.aplicarCatalogo(this.conocimientosApi.listaEnSesion());
    this.buscar();
    setTimeout(() => this.cargarFiltros(), 0);
    this.querySub = this.route.queryParamMap.subscribe((params) => {
      const q = params.get('q') || '';
      if (q !== this.texto) {
        this.texto = q;
        this.buscar();
      }
    });
  }

  ngOnDestroy(): void {
    this.querySub?.unsubscribe();
    this.busquedaSub?.unsubscribe();
    this.listaSub?.unsubscribe();
    if (this.debounceBusqueda) {
      clearTimeout(this.debounceBusqueda);
    }
  }

  get filtrosActivos(): number {
    return [this.hardwareId, this.sistemaId, this.moduloId, this.frecuenciaId].filter((id) => id != null).length;
  }

  get borradoresFiltrados(): Conocimiento[] {
    const q = this.texto.trim().toLowerCase();
    return this.borradores.filter((item) => {
      if (this.hardwareId && item.hardwareId !== this.hardwareId) {
        return false;
      }
      if (this.sistemaId && item.sistemaId !== this.sistemaId) {
        return false;
      }
      if (this.moduloId && item.moduloId !== this.moduloId) {
        return false;
      }
      if (this.frecuenciaId && item.frecuenciaId !== this.frecuenciaId) {
        return false;
      }
      if (!q) {
        return true;
      }
      return (
        item.titulo.toLowerCase().includes(q) ||
        (item.descripcion || '').toLowerCase().includes(q) ||
        (item.hardwareNombre || '').toLowerCase().includes(q) ||
        (item.sistemaNombre || '').toLowerCase().includes(q)
      );
    });
  }

  mostrarPestana(pestana: 'publicados' | 'borradores'): void {
    this.pestana = pestana;
    if (pestana === 'publicados') {
      this.buscar();
    }
  }

  onTextoChange(): void {
    this.programarBusqueda(280);
  }

  onHardwareChange(): void {
    this.sistemaId = null;
    this.moduloId = null;
    this.modulos = [];
    this.cargarSistemas();
    this.programarBusqueda(0);
  }

  onSistemaChange(): void {
    this.moduloId = null;
    this.cargarModulos();
    this.programarBusqueda(0);
  }

  onFiltroChange(): void {
    this.programarBusqueda(0);
  }

  private programarBusqueda(delayMs: number): void {
    if (this.debounceBusqueda) {
      clearTimeout(this.debounceBusqueda);
    }
    this.debounceBusqueda = setTimeout(() => {
      if (this.pestana === 'publicados') {
        this.buscar();
      }
    }, delayMs);
  }

  buscar(): void {
    this.busquedaSub?.unsubscribe();
    this.listaSub?.unsubscribe();
    this.error = '';
    const texto = this.texto.trim();
    if (!texto) {
      if (this.catalogo.length) {
        this.publicarVista(this.catalogo);
      }
      this.loading = this.resultados.length === 0;
      this.busquedaSub = this.conocimientosApi.listar().subscribe({
        next: (items) => this.aplicarCatalogo(items),
        error: (err: Error) => {
          this.buscado = true;
          this.loading = false;
          if (!this.resultados.length) {
            this.error = err.message;
          }
        },
      });
      return;
    }

    const locales = this.conocimientosApi.parecidos(texto, 50).filter((item) => this.coincideFiltro(item));
    if (locales.length) {
      this.resultados = locales.map((item) => this.aResultado(item));
      this.buscado = true;
      this.loading = false;
    } else {
      this.loading = this.resultados.length === 0;
    }
    this.listaSub = this.conocimientosApi.listar().subscribe({
      next: () => {
        if (!this.loading) {
          return;
        }
        const encontrados = this.conocimientosApi.parecidos(texto, 50).filter((item) => this.coincideFiltro(item));
        if (!encontrados.length) {
          return;
        }
        this.resultados = encontrados.map((item) => this.aResultado(item));
        this.buscado = true;
        this.loading = false;
      },
    });
    this.busquedaSub = this.conocimientosApi
      .buscar({
        texto,
        hardwareId: this.hardwareId,
        sistemaId: this.sistemaId,
        moduloId: this.moduloId,
        frecuenciaId: this.frecuenciaId,
      })
      .subscribe({
        next: (items) => {
          if (items.length || !locales.length) {
            this.resultados = items;
          }
          this.buscado = true;
          this.loading = false;
        },
        error: (err: Error) => {
          this.buscado = true;
          this.loading = false;
          if (!this.resultados.length) {
            this.error = err.message;
          }
        },
      });
  }

  limpiar(): void {
    this.texto = '';
    this.hardwareId = null;
    this.sistemaId = null;
    this.moduloId = null;
    this.frecuenciaId = null;
    this.modulos = [];
    this.error = '';
    this.cargarSistemas();
    this.buscar();
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { q: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  abrir(item: { id: number }): void {
    this.conocimientosApi.precargarGuia(item.id, true);
    void this.router.navigate(['/conocimiento', item.id]);
  }

  publicar(item: Conocimiento): void {
    if (!this.esAdministrador() || item.estado !== 'BORRADOR' || this.publicandoId != null) {
      return;
    }
    this.publicandoId = item.id;
    this.error = '';
    this.conocimientosApi.cambiarEstado(item.id, 'PUBLICADO').subscribe({
      next: () => {
        this.borradores = this.borradores.filter((actual) => actual.id !== item.id);
        this.publicandoId = null;
        this.aviso = 'El conocimiento quedó publicado y ya se puede consultar.';
        this.loading = false;
        this.buscar();
      },
      error: (err: Error) => {
        this.publicandoId = null;
        this.error = err.message;
      },
    });
  }

  editar(item: { id: number }, event: Event): void {
    event.stopPropagation();
    void this.router.navigate(['/conocimiento', item.id, 'editar']);
  }

  pedirEliminar(item: { id: number; titulo: string }, event: Event): void {
    event.stopPropagation();
    if (this.eliminando) {
      return;
    }
    this.pendienteEliminar = item;
  }

  cancelarEliminar(): void {
    if (this.eliminando) {
      return;
    }
    this.pendienteEliminar = null;
  }

  confirmarEliminar(): void {
    const item = this.pendienteEliminar;
    if (!item || this.eliminando) {
      return;
    }
    this.eliminando = true;
    this.error = '';
    this.conocimientosApi.eliminar(item.id).subscribe({
      next: () => {
        this.resultados = this.resultados.filter((actual) => actual.id !== item.id);
        this.borradores = this.borradores.filter((actual) => actual.id !== item.id);
        this.pendienteEliminar = null;
        this.eliminando = false;
        this.aviso = 'El conocimiento dejó de estar disponible para consulta y búsqueda.';
      },
      error: (err: Error) => {
        this.eliminando = false;
        this.pendienteEliminar = null;
        this.aviso = '';
        this.error = err.message;
      },
    });
  }

  esFavorito(id: number): boolean {
    return this.favoritosService.tiene(id);
  }

  toggleFavorito(id: number, titulo: string, event: Event): void {
    event.stopPropagation();
    if (this.favoritosService.tiene(id)) {
      this.pendienteQuitarFavorito = { id, titulo };
      return;
    }
    this.favoritosService.toggle(id, titulo);
  }

  cancelarQuitarFavorito(): void {
    this.pendienteQuitarFavorito = null;
  }

  confirmarQuitarFavorito(): void {
    const item = this.pendienteQuitarFavorito;
    if (!item) {
      return;
    }
    this.favoritosService.quitar(item.id);
    this.pendienteQuitarFavorito = null;
  }

  descripcionDe(id: number): string {
    return this.detalles.get(id)?.descripcion || '';
  }

  fechaDe(id: number): string | null {
    const item = this.detalles.get(id);
    return item?.fechaModificacion || item?.fechaCreacion || null;
  }

  iconoDe(item: { titulo: string; hardwareNombre?: string | null; sistemaNombre?: string | null }): TipoIcono {
    const texto = `${item.hardwareNombre || ''} ${item.sistemaNombre || ''} ${item.titulo}`.toLowerCase();
    if (/impres|printer|tsp|zebra|etiquet/.test(texto)) {
      return 'impresora';
    }
    if (/\bred\b|switch|firewall|enlace|router|wifi|vpn|internet|conectividad/.test(texto)) {
      return 'red';
    }
    if (/servidor|server|plan b|\bvm\b|respaldo|backup/.test(texto)) {
      return 'servidor';
    }
    if (/\bpos\b|jadima|pantalla|monitor|equipo|\bpc\b|notebook|caja/.test(texto)) {
      return 'pantalla';
    }
    return 'documento';
  }

  private cargarFiltros(): void {
    this.clasificacion.listarHardware().subscribe({
      next: (items) => (this.hardwares = items),
    });
    this.clasificacion.listarFrecuencias().subscribe({
      next: (data) => (this.frecuencias = data.items),
    });
    this.cargarSistemas();
  }

  private aplicarCatalogo(items: Conocimiento[]): void {
    this.catalogo = items;
    this.detalles = new Map(items.map((item) => [item.id, item]));
    this.borradores = this.puedeGestionar() ? items.filter((item) => item.estado === 'BORRADOR') : [];
    if (!this.texto.trim()) {
      this.publicarVista(items);
    }
    items.slice(0, 12).forEach((item) => this.conocimientosApi.precargarGuia(item.id));
  }

  private publicarVista(items: Conocimiento[]): void {
    this.resultados = items
      .filter((item) => item.estado === 'PUBLICADO' && this.coincideFiltro(item))
      .map((item) => this.aResultado(item));
    this.buscado = true;
    this.loading = false;
  }

  private coincideFiltro(item: Conocimiento): boolean {
    if (this.hardwareId && item.hardwareId !== this.hardwareId) {
      return false;
    }
    if (this.sistemaId && item.sistemaId !== this.sistemaId) {
      return false;
    }
    if (this.moduloId && item.moduloId !== this.moduloId) {
      return false;
    }
    if (this.frecuenciaId && item.frecuenciaId !== this.frecuenciaId) {
      return false;
    }
    return true;
  }

  private aResultado(item: Conocimiento): ResultadoBusquedaConocimiento {
    return {
      id: item.id,
      titulo: item.titulo,
      hardwareId: item.hardwareId,
      hardwareNombre: item.hardwareNombre || null,
      sistemaId: item.sistemaId,
      sistemaNombre: item.sistemaNombre || null,
      moduloId: item.moduloId,
      moduloNombre: item.moduloNombre || null,
      frecuenciaId: item.frecuenciaId,
      frecuenciaNombre: item.frecuenciaNombre || null,
      relevancia: null,
    };
  }

  private cargarSistemas(): void {
    const consulta = this.hardwareId
      ? this.clasificacion.sistemasDeHardware(this.hardwareId)
      : this.clasificacion.listarSistemas();
    consulta.subscribe({
      next: (items) => {
        this.sistemas = items;
        if (this.sistemaId && !items.some((item) => item.id === this.sistemaId)) {
          this.sistemaId = null;
          this.moduloId = null;
          this.modulos = [];
        }
      },
    });
  }

  private cargarModulos(): void {
    if (!this.sistemaId) {
      this.modulos = [];
      this.moduloId = null;
      return;
    }
    this.clasificacion.modulosDeSistema(this.sistemaId).subscribe({
      next: (items) => {
        this.modulos = items;
        if (this.moduloId && !items.some((item) => item.id === this.moduloId)) {
          this.moduloId = null;
        }
      },
    });
  }
}
