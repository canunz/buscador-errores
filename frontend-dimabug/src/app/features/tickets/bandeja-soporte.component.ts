import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, Subject, catchError, debounceTime, distinctUntilChanged, combineLatest, map, of } from 'rxjs';
import { esStaff } from '../../core/models/usuario.model';
import {
  ESTADO_INFO,
  ESTADOS_TICKET,
  Etiqueta,
  EstadoTicket,
  PRIORIDADES_TICKET,
  PRIORIDAD_INFO,
  PrioridadTicket,
  Referencia,
  TicketResumen,
  colorTextoEtiqueta,
  fechaLarga,
  fechaRelativa,
} from '../../core/models/ticket.model';
import { AuthService } from '../../core/services/auth.service';
import { EtiquetaService } from '../../core/services/etiqueta.service';
import { TicketService } from '../../core/services/ticket.service';
import { UsuarioService } from '../../core/services/usuario.service';

@Component({
  selector: 'app-bandeja-soporte',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './bandeja-soporte.component.html',
  styleUrls: ['./tickets-comun.css', './bandeja-soporte.component.css'],
})
export class BandejaSoporteComponent implements OnInit {
  private readonly tickets = inject(TicketService);
  private readonly etiquetasApi = inject(EtiquetaService);
  private readonly usuariosApi = inject(UsuarioService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly busqueda$ = new Subject<string>();

  readonly estados = ESTADOS_TICKET;
  readonly prioridades = PRIORIDADES_TICKET;
  readonly estadoInfo = ESTADO_INFO;
  readonly prioridadInfo = PRIORIDAD_INFO;
  readonly colorTexto = colorTextoEtiqueta;
  readonly relativa = fechaRelativa;
  readonly larga = fechaLarga;
  readonly esAdmin = this.auth.isAdmin();
  readonly miId = this.auth.usuario()?.usuarioId ?? null;

  items: TicketResumen[] = [];
  etiquetas: Etiqueta[] = [];
  tecnicos: Referencia[] = [];
  conteos: Partial<Record<EstadoTicket | 'TODOS', number | null>> = {};

  /** Qué significa cada estado, en palabras simples. */
  readonly ayudaEstado: Record<EstadoTicket, string> = {
    ABIERTO: 'Nadie lo ha tomado',
    EN_PROGRESO: 'Un técnico lo atiende',
    EN_ESPERA: 'Esperan al usuario',
    RESUELTO: 'Falta que el usuario confirme',
    CERRADO: 'Terminado',
    CANCELADO: 'Anulado',
  };

  estado: EstadoTicket | null = null;
  prioridad: PrioridadTicket | null = null;
  etiquetaId: number | null = null;
  asignadoId: number | null = null;
  texto = '';

  pagina = 0;
  readonly tamano = 15;
  total = 0;
  totalPaginas = 0;
  cargando = true;
  tomando: number | null = null;
  error = '';
  errorCarga = '';
  /** Críticos que siguen abiertos, en progreso o en espera. */
  criticosActivos = 0;
  aviso = '';
  /** Lista completa en el navegador: filtrar y contar no vuelve a pegarle al servidor. */
  private todos: TicketResumen[] = [];
  private enServidor = false;

  ngOnInit(): void {
    // Filtros que llegan desde el panel de Inicio (?estado=...&prioridad=...).
    const params = this.route.snapshot.queryParamMap;
    const estadoParam = params.get('estado') as EstadoTicket | null;
    const prioridadParam = params.get('prioridad') as PrioridadTicket | null;
    if (estadoParam && this.estados.includes(estadoParam)) this.estado = estadoParam;
    if (prioridadParam && this.prioridades.includes(prioridadParam)) this.prioridad = prioridadParam;
    const asignadoParam = Number(params.get('asignadoId'));
    if (asignadoParam > 0) this.asignadoId = asignadoParam;

    this.busqueda$
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.recargarDesdeInicio());
    setTimeout(() => this.cargarFiltros(), 1200);
    this.cargar();
  }

  get hayFiltros(): boolean {
    return !!(this.prioridad || this.etiquetaId || this.asignadoId || this.texto.trim());
  }

  buscar(texto: string): void {
    this.busqueda$.next(texto.trim());
  }

  elegirEstado(estado: EstadoTicket | null): void {
    this.estado = estado;
    this.recargarDesdeInicio();
  }

  recargarDesdeInicio(): void {
    this.pagina = 0;
    if (this.enServidor) this.cargarServidor();
    else this.aplicarLocal();
  }

  limpiarFiltros(): void {
    this.prioridad = null;
    this.etiquetaId = null;
    this.asignadoId = null;
    this.texto = '';
    this.recargarDesdeInicio();
  }

  irAPagina(pagina: number): void {
    if (pagina < 0 || pagina >= this.totalPaginas) return;
    this.pagina = pagina;
    if (this.enServidor) this.cargarServidor();
    else this.aplicarLocal();
  }

  abrir(ticket: TicketResumen): void {
    this.router.navigate(['/tickets', ticket.id]);
  }

  /** Atajo para que soporte se asigne un ticket abierto sin entrar al detalle. */
  tomar(ticket: TicketResumen, evento: Event): void {
    evento.stopPropagation();
    if (!this.miId || this.tomando) return;
    this.tomando = ticket.id;
    this.tickets.asignar(ticket.id, this.miId).subscribe({
      next: (actualizado) => {
        this.tomando = null;
        this.aviso = `Tomaste el ticket ${actualizado.codigo}.`;
        this.cargar();
      },
      error: (err: Error) => {
        this.tomando = null;
        this.error = err.message;
      },
    });
  }

  cargar(): void {
    this.error = '';
    this.errorCarga = '';
    this.cargando = this.items.length === 0;
    this.tickets.todos().subscribe({
      next: (lista) => {
        this.enServidor = false;
        this.todos = lista;
        this.pintarConteos(this.todos);
        this.aplicarLocal();
      },
      error: (err: Error) => {
        if (this.items.length) return;
        this.errorCarga = err.message;
        this.cargando = false;
      },
    });
  }

  private cargarServidor(): void {
    this.cargando = this.items.length === 0;
    this.tickets
      .listar({
        estado: this.estado,
        prioridad: this.prioridad,
        etiquetaId: this.etiquetaId,
        asignadoId: this.asignadoId,
        texto: this.texto,
        page: this.pagina,
        size: this.tamano,
      })
      .subscribe({
        next: (pagina) => {
          this.items = pagina.contenido;
          this.total = pagina.totalElementos;
          this.totalPaginas = pagina.totalPaginas;
          this.cargando = false;
        },
        error: (err: Error) => {
          if (this.items.length) return;
          this.errorCarga = err.message;
          this.cargando = false;
        },
      });
  }

  private pintarConteos(lista: TicketResumen[]): void {
    const conteos: Partial<Record<EstadoTicket | 'TODOS', number | null>> = { TODOS: lista.length };
    for (const estado of this.estados) conteos[estado] = lista.filter((t) => t.estado === estado).length;
    this.conteos = conteos;
    this.criticosActivos = lista.filter(
      (t) => t.prioridad === 'CRITICA' && (t.estado === 'ABIERTO' || t.estado === 'EN_PROGRESO' || t.estado === 'EN_ESPERA'),
    ).length;
  }

  private aplicarLocal(): void {
    const texto = this.texto.trim().toLowerCase();
    const filtrados = this.todos.filter((t) => {
      if (this.estado && t.estado !== this.estado) return false;
      if (this.prioridad && t.prioridad !== this.prioridad) return false;
      if (this.asignadoId && t.asignado?.id !== this.asignadoId) return false;
      if (this.etiquetaId && !t.etiquetas.some((e) => e.id === this.etiquetaId)) return false;
      if (!texto) return true;
      return `${t.codigo} ${t.titulo}`.toLowerCase().includes(texto);
    });
    this.total = filtrados.length;
    this.totalPaginas = Math.max(1, Math.ceil(filtrados.length / this.tamano) || 1);
    if (this.pagina >= this.totalPaginas) this.pagina = Math.max(0, this.totalPaginas - 1);
    this.items = filtrados.slice(this.pagina * this.tamano, this.pagina * this.tamano + this.tamano);
    this.cargando = false;
  }

  private cargarFiltros(): void {
    this.etiquetasApi.listar().subscribe({ next: (lista) => (this.etiquetas = lista), error: () => undefined });
    if (!this.esAdmin) return;
    this.usuariosApi.listar().subscribe({
      next: (usuarios) =>
        (this.tecnicos = usuarios
          .filter((u) => u.usuarioEstado !== false && esStaff(u))
          .map((u) => ({ id: u.usuarioId, nombre: u.usuarioNombre }))),
      error: () => undefined,
    });
  }

  reintentar(): void {
    this.cargar();
    this.cargarConteos();
  }

  private cargarConteos(): void {
    // Una sola llamada trae todos los contadores. Si el servidor es de la versión anterior, se usa el cálculo antiguo.
    this.tickets.conteo().subscribe({
      next: (c) => {
        if (!c) {
          this.cargarConteosAntiguo();
          return;
        }
        const conteos: Record<string, number | null> = { TODOS: c.total };
        for (const estado of this.estados) conteos[estado] = c.porEstado[estado] ?? 0;
        this.conteos = conteos;
        this.criticosActivos = c.criticosActivos;
      },
      error: () => {
        // Si el servidor falla se muestra "–" en vez de un 0 engañoso.
        this.conteos = { TODOS: null, ...Object.fromEntries(this.estados.map((e) => [e, null])) };
      },
    });
  }

  private cargarConteosAntiguo(): void {
    const llamadas: Record<string, Observable<number | null>> = {
      TODOS: this.tickets.contar(null).pipe(catchError(() => of(null))),
    };
    for (const estado of this.estados) {
      llamadas[estado] = this.tickets.contar(estado).pipe(catchError(() => of(null)));
    }
    combineLatest(llamadas).subscribe((conteos) => (this.conteos = conteos));
    this.tickets
      .listar({ prioridad: 'CRITICA', page: 0, size: 50 })
      .pipe(
        map((pagina) => pagina.contenido.filter((t) => ['ABIERTO', 'EN_PROGRESO', 'EN_ESPERA'].includes(t.estado)).length),
        catchError(() => of(0)),
      )
      .subscribe((n) => (this.criticosActivos = n));
  }

  verCriticos(): void {
    this.estado = null;
    this.prioridad = 'CRITICA';
    this.recargarDesdeInicio();
  }
}
