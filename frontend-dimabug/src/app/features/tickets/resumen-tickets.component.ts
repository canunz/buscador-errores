import { Component, OnInit, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
  ESTADO_INFO,
  EstadoTicket,
  PRIORIDAD_INFO,
  PrioridadTicket,
  TicketResumen,
  fechaLarga,
  fechaRelativa,
} from '../../core/models/ticket.model';
import { AuthService } from '../../core/services/auth.service';
import { TicketService } from '../../core/services/ticket.service';

type Vista = 'admin' | 'soporte' | 'usuario';

interface Indicador {
  clave: string;
  etiqueta: string;
  detalle: string;
  valor: number | null;
  tono: 'azul' | 'rojo' | 'ambar' | 'verde' | 'violeta' | 'gris';
  alerta?: boolean;
  filtro: Record<string, string | number>;
}

interface CargaTecnico {
  nombre: string;
  activos: number;
  criticos: number;
  ancho: number;
}

const ACTIVOS: EstadoTicket[] = ['ABIERTO', 'EN_PROGRESO', 'EN_ESPERA'];
const PESO_PRIORIDAD: Record<PrioridadTicket, number> = { CRITICA: 0, ALTA: 1, MEDIA: 2, BAJA: 3 };

/**
 * Panel de tickets del Inicio, distinto para cada rol:
 * - Administrador: supervisión (qué falta asignar, carga de cada técnico, críticos).
 * - Soporte: su cola de trabajo (lo asignado a él, lo que puede tomar, críticos).
 * - Usuario: el estado de sus propios tickets.
 */
@Component({
  selector: 'app-resumen-tickets',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './resumen-tickets.component.html',
  styleUrls: ['./tickets-comun.css', './resumen-tickets.component.css'],
})
export class ResumenTicketsComponent implements OnInit {
  private readonly tickets = inject(TicketService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly vista: Vista = this.auth.isAdmin() ? 'admin' : this.auth.isStaff() ? 'soporte' : 'usuario';
  readonly miId = this.auth.usuario()?.usuarioId ?? 0;
  readonly estadoInfo = ESTADO_INFO;
  readonly prioridadInfo = PRIORIDAD_INFO;
  readonly relativa = fechaRelativa;
  readonly larga = fechaLarga;
  readonly rutaLista = this.vista === 'usuario' ? '/tickets' : '/tickets/bandeja';

  indicadores: Indicador[] = [];
  lista: TicketResumen[] = [];
  carga: CargaTecnico[] = [];
  cargando = true;
  sinServicio = false;

  readonly textos: Record<Vista, { titulo: string; lista: string; vacio: string }> = {
    admin: {
      titulo: 'Supervisión de tickets',
      lista: 'Tickets sin técnico: asígnalos si nadie los toma',
      vacio: 'Todos los tickets tienen un técnico a cargo.',
    },
    soporte: {
      titulo: 'Tu trabajo de hoy',
      lista: 'Tu cola de trabajo',
      vacio: 'No tienes tickets asignados. Revisa la bandeja por si hay alguno sin técnico.',
    },
    usuario: {
      titulo: 'Tus tickets',
      lista: 'Necesitan tu atención',
      vacio: 'No tienes tickets pendientes de tu parte.',
    },
  };

  ngOnInit(): void {
    this.tickets.todos().subscribe({
      next: (lista) => this.pintar(lista),
      error: () => {
        this.sinServicio = this.indicadores.length === 0;
        this.cargando = false;
      },
    });
  }

  private pintar(todos: TicketResumen[]): void {
    this.sinServicio = false;
    if (this.vista === 'admin') this.pintarAdmin(todos);
    else if (this.vista === 'soporte') this.pintarSoporte(todos);
    else this.pintarUsuario(todos);
    this.cargando = false;
  }

  /** En el inicio del usuario el estado se dice como una acción suya, no con el nombre interno. */
  estadoUsuario(estado: EstadoTicket): string {
    switch (estado) {
      case 'EN_ESPERA':
        return 'Responde';
      case 'RESUELTO':
        return 'Por confirmar';
      case 'EN_PROGRESO':
        return 'En revisión';
      case 'ABIERTO':
        return 'Recibido';
      case 'CERRADO':
        return 'Cerrado';
      case 'CANCELADO':
        return 'Cancelado';
    }
  }

  textoUsuario(ticket: TicketResumen): string {
    const cuando = this.relativa(ticket.fechaActualizacion);
    if (ticket.estado === 'EN_ESPERA') return `Soporte te escribió ${cuando}`;
    if (ticket.estado === 'RESUELTO') return `Quedó resuelto ${cuando}`;
    return cuando;
  }

  abrir(ticket: TicketResumen): void {
    void this.router.navigate(['/tickets', ticket.id]);
  }

  irA(indicador: Indicador): void {
    void this.router.navigate([this.rutaLista], { queryParams: indicador.filtro });
  }

  // ------------------------------------------------------------ administrador

  private pintarAdmin(todos: TicketResumen[]): void {
      const lista = todos.filter((t) => ACTIVOS.includes(t.estado));
      const cerrados = todos.filter((t) => t.estado === 'CERRADO').length;
      const sinTecnico = lista.filter((t) => !t.asignado);
      const criticos = lista.filter((t) => t.prioridad === 'CRITICA');
      this.indicadores = [
        {
          clave: 'asignar',
          etiqueta: 'Sin técnico',
          detalle: 'Soporte aún no los toma',
          valor: sinTecnico.length,
          tono: 'azul',
          alerta: sinTecnico.some((t) => t.prioridad === 'CRITICA'),
          filtro: { estado: 'ABIERTO' },
        },
        {
          clave: 'criticos',
          etiqueta: 'Críticos activos',
          detalle: 'Revisa que alguien los esté atendiendo',
          valor: criticos.length,
          tono: 'rojo',
          alerta: criticos.length > 0,
          filtro: { prioridad: 'CRITICA' },
        },
        {
          clave: 'activos',
          etiqueta: 'En curso',
          detalle: 'Abiertos, en progreso o en espera',
          valor: lista.length,
          tono: 'violeta',
          filtro: {},
        },
        {
          clave: 'cerrados',
          etiqueta: 'Cerrados',
          detalle: 'Confirmados por el usuario',
          valor: cerrados,
          tono: 'verde',
          filtro: { estado: 'CERRADO' },
        },
      ];
      this.carga = this.cargaPorTecnico(lista);
      this.lista = this.ordenar(sinTecnico).slice(0, 5);
  }

  private cargaPorTecnico(activos: TicketResumen[]): CargaTecnico[] {
    const porTecnico = new Map<string, { activos: number; criticos: number }>();
    for (const t of activos) {
      if (!t.asignado) continue;
      const nombre = t.asignado.nombre || `Técnico ${t.asignado.id}`;
      const actual = porTecnico.get(nombre) ?? { activos: 0, criticos: 0 };
      actual.activos++;
      if (t.prioridad === 'CRITICA') actual.criticos++;
      porTecnico.set(nombre, actual);
    }
    const maximo = Math.max(1, ...[...porTecnico.values()].map((v) => v.activos));
    return [...porTecnico.entries()]
      .map(([nombre, v]) => ({ nombre, ...v, ancho: Math.round((v.activos / maximo) * 100) }))
      .sort((a, b) => b.activos - a.activos);
  }

  // ------------------------------------------------------------ soporte

  private pintarSoporte(todos: TicketResumen[]): void {
      const activos = todos.filter((t) => ACTIVOS.includes(t.estado));
      const misActivos = activos.filter((t) => t.asignado?.id === this.miId);
      const sinTecnico = activos.filter((t) => !t.asignado);
      const criticos = activos.filter((t) => t.prioridad === 'CRITICA');
      this.indicadores = [
        {
          clave: 'mios',
          etiqueta: 'Asignados a ti',
          detalle: 'Tickets que estás atendiendo',
          valor: misActivos.length,
          tono: 'violeta',
          filtro: { asignadoId: this.miId },
        },
        {
          clave: 'tomar',
          etiqueta: 'Sin técnico',
          detalle: 'Puedes tomarlos desde la bandeja',
          valor: sinTecnico.length,
          tono: 'azul',
          alerta: sinTecnico.some((t) => t.prioridad === 'CRITICA'),
          filtro: { estado: 'ABIERTO' },
        },
        {
          clave: 'criticos',
          etiqueta: 'Críticos activos',
          detalle: 'Necesitan atención inmediata',
          valor: criticos.length,
          tono: 'rojo',
          alerta: criticos.length > 0,
          filtro: { prioridad: 'CRITICA' },
        },
        {
          clave: 'espera',
          etiqueta: 'Esperando al usuario',
          detalle: 'De los tuyos, en espera de respuesta',
          valor: misActivos.filter((t) => t.estado === 'EN_ESPERA').length,
          tono: 'ambar',
          filtro: { asignadoId: this.miId, estado: 'EN_ESPERA' },
        },
      ];
      this.lista = this.ordenar(misActivos).slice(0, 6);
  }

  // ------------------------------------------------------------ usuario

  private pintarUsuario(todos: TicketResumen[]): void {
      const abiertos = todos.filter((t) => t.estado === 'ABIERTO').length;
      const enCurso = todos.filter((t) => t.estado === 'EN_PROGRESO').length;
      const enEspera = todos.filter((t) => t.estado === 'EN_ESPERA');
      const resueltos = todos.filter((t) => t.estado === 'RESUELTO');
      this.indicadores = [
        {
          clave: 'confirmar',
          etiqueta: 'Por confirmar',
          detalle: 'Cuéntanos si quedó solucionado',
          valor: resueltos.length,
          tono: 'verde',
          alerta: resueltos.length > 0,
          filtro: { estado: 'RESUELTO' },
        },
        {
          clave: 'espera',
          etiqueta: 'Esperan tu respuesta',
          detalle: 'Soporte te hizo una pregunta',
          valor: enEspera.length,
          tono: 'ambar',
          filtro: { estado: 'EN_ESPERA' },
        },
        {
          clave: 'curso',
          etiqueta: 'En revisión',
          detalle: 'Un técnico lo está viendo',
          valor: enCurso,
          tono: 'violeta',
          filtro: { estado: 'EN_PROGRESO' },
        },
        {
          clave: 'abiertos',
          etiqueta: 'Recibidos',
          detalle: 'Aún sin técnico asignado',
          valor: abiertos,
          tono: 'azul',
          filtro: { estado: 'ABIERTO' },
        },
      ];
      this.lista = [...resueltos, ...enEspera].slice(0, 4);
  }

  /** Críticos primero, luego por prioridad y antigüedad. */
  private ordenar(lista: TicketResumen[]): TicketResumen[] {
    return [...lista].sort(
      (a, b) =>
        PESO_PRIORIDAD[a.prioridad] - PESO_PRIORIDAD[b.prioridad] || a.fechaCreacion.localeCompare(b.fechaCreacion),
    );
  }
}
