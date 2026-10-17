import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';
import { EstadoTicket, TicketResumen } from '../models/ticket.model';
import { esAdministrador, esStaff } from '../models/usuario.model';
import { AuthService } from './auth.service';
import type { Aviso } from './notificacion.service';
import { TicketService } from './ticket.service';

const ACTIVOS: EstadoTicket[] = ['ABIERTO', 'EN_PROGRESO', 'EN_ESPERA'];

/** Marca mínima de un ticket para saber si cambió desde la última vez que la persona lo abrió. */
interface MarcaTicket {
  id: number;
  fechaActualizacion: string;
}

/**
 * Avisos de tickets para la campana:
 * - Soporte y administración: tickets nuevos sin técnico (los críticos van primero y destacados)
 *   y tickets críticos que siguen activos.
 * - Usuario: cuando soporte le pide una respuesta o resolvió su ticket y falta que confirme.
 * El id del aviso incluye el estado, así un cambio de estado vuelve a avisar.
 */
@Injectable({ providedIn: 'root' })
export class TicketAvisosService {
  private readonly auth = inject(AuthService);
  private readonly tickets = inject(TicketService);

  /**
   * Se llama al abrir (o actualizar) un ticket: lo que ya viste no vuelve a avisar.
   * Así tampoco te avisa de los cambios que hiciste tú mismo.
   */
  marcarVisto(ticket: MarcaTicket): void {
    const vistos = this.vistos();
    if ((vistos[ticket.id] ?? '') >= ticket.fechaActualizacion) return;
    vistos[ticket.id] = ticket.fechaActualizacion;
    try {
      localStorage.setItem(this.claveVistos(), JSON.stringify(vistos));
    } catch {
      // Sin almacenamiento solo se pierde el recuerdo de lo visto.
    }
  }

  private hayNovedad(t: MarcaTicket): boolean {
    const visto = this.vistos()[t.id];
    return !visto || t.fechaActualizacion > visto;
  }

  private claveVistos(): string {
    return `dimabug.tickets.vistos.${this.auth.usuario()?.usuarioId ?? 0}`;
  }

  private vistos(): Record<number, string> {
    try {
      const raw = localStorage.getItem(this.claveVistos());
      const parsed = raw ? JSON.parse(raw) : {};
      return parsed && typeof parsed === 'object' ? (parsed as Record<number, string>) : {};
    } catch {
      return {};
    }
  }

  /** Últimos avisos ya calculados, para pintar la campana sin esperar al servidor. */
  enCache(): Aviso[] {
    try {
      const raw = localStorage.getItem(this.claveCache());
      const parsed = raw ? (JSON.parse(raw) as Aviso[]) : [];
      return Array.isArray(parsed) ? parsed.filter((aviso) => !!aviso?.id && !!aviso.titulo) : [];
    } catch {
      return [];
    }
  }

  avisos(): Observable<Aviso[]> {
    const yo = this.auth.usuario();
    if (!yo) {
      return of([]);
    }
    return (esStaff(yo) ? this.avisosStaff(esAdministrador(yo), yo.usuarioId) : this.avisosUsuario()).pipe(
      tap((avisos) => this.guardarCache(avisos)),
      catchError(() => of(this.enCache())),
    );
  }

  private claveCache(): string {
    return `dimabug.notif.tickets.${this.auth.usuario()?.usuarioId ?? 0}`;
  }

  private guardarCache(avisos: Aviso[]): void {
    try {
      localStorage.setItem(this.claveCache(), JSON.stringify(avisos));
    } catch {
      // La campana sigue con lo que ya tenía en memoria.
    }
  }

  private avisosStaff(admin: boolean, miId: number): Observable<Aviso[]> {
    return this.tickets.todos().pipe(
      map((todos) => {
        const abiertos = todos.filter((t) => t.estado === 'ABIERTO');
        const criticos = todos.filter((t) => t.prioridad === 'CRITICA');
        const mios = todos.filter((t) => t.asignado?.id === miId);
        const avisos = new Map<string, Aviso>();
        // Tickets que me asignaron o donde el usuario respondió desde la última vez que los abrí.
        for (const t of mios.filter((x) => ACTIVOS.includes(x.estado) && this.hayNovedad(x))) {
          const nuevo = !this.vistos()[t.id];
          const id = `ticket-${t.id}-MIO-${t.fechaActualizacion}`;
          avisos.set(id, {
            id,
            titulo: nuevo ? `Te asignaron el ticket ${t.codigo}` : `Novedades en ${t.codigo}`,
            detalle: nuevo
              ? `${t.titulo} — reportado por ${t.solicitante?.nombre || 'un usuario'}.`
              : `${t.titulo} — ${t.solicitante?.nombre || 'El usuario'} respondió o hubo un cambio.`,
            fecha: t.fechaActualizacion,
            ruta: `/tickets/${t.id}`,
          });
        }
        for (const t of abiertos.filter((x) => !x.asignado)) {
          const critico = t.prioridad === 'CRITICA';
          avisos.set(`ticket-${t.id}-ABIERTO`, {
            id: `ticket-${t.id}-ABIERTO`,
            titulo: critico ? `Ticket CRÍTICO sin técnico · ${t.codigo}` : `Nuevo ticket · ${t.codigo}`,
            detalle: `${t.titulo} — reportado por ${t.solicitante?.nombre || 'un usuario'}${admin ? '. Puedes asignarlo a un técnico.' : '. Puedes tomarlo.'}`,
            fecha: t.fechaCreacion,
            ruta: `/tickets/${t.id}`,
          });
        }
        for (const t of criticos.filter((x) => ACTIVOS.includes(x.estado) && x.asignado)) {
          const id = `ticket-${t.id}-CRITICO-${t.estado}`;
          avisos.set(id, {
            id,
            titulo: `Ticket CRÍTICO en curso · ${t.codigo}`,
            detalle: `${t.titulo} — ${t.estado === 'EN_ESPERA' ? 'en espera del usuario' : 'lo atiende ' + (t.asignado?.nombre || 'un técnico')}.`,
            fecha: t.fechaActualizacion,
            ruta: `/tickets/${t.id}`,
          });
        }
        return [...avisos.values()];
      }),
    );
  }

  /** Todo cambio en tus tickets que no hiciste tú (soporte lo tomó, respondió, lo resolvió…). */
  private avisosUsuario(): Observable<Aviso[]> {
    return this.tickets.todos().pipe(
      map((tickets) =>
        tickets
          .filter((t) => this.hayNovedad(t) && (this.vistos()[t.id] !== undefined || t.estado !== 'ABIERTO'))
          .map((t) => {
            const textos: Record<EstadoTicket, [string, string]> = {
              ABIERTO: [`Novedades en tu ticket ${t.codigo}`, 'Revisa la conversación del ticket.'],
              EN_PROGRESO: [
                `${t.asignado?.nombre || 'Un técnico'} está atendiendo ${t.codigo}`,
                'Hay novedades en tu ticket. Revisa la conversación.',
              ],
              EN_ESPERA: [`Soporte necesita tu respuesta · ${t.codigo}`, 'Responde en la conversación para que puedan seguir.'],
              RESUELTO: [`Tu ticket ${t.codigo} fue resuelto`, 'Confirma si quedó solucionado.'],
              CERRADO: [`Tu ticket ${t.codigo} quedó cerrado`, 'El caso se dio por terminado.'],
              CANCELADO: [`Tu ticket ${t.codigo} fue cancelado`, 'Revisa el motivo en el ticket.'],
            };
            const [titulo, detalle] = textos[t.estado];
            return {
              id: `ticket-${t.id}-${t.estado}-${t.fechaActualizacion}`,
              titulo,
              detalle: `${t.titulo} — ${detalle}`,
              fecha: t.fechaActualizacion,
              ruta: `/tickets/${t.id}`,
            };
          }),
      ),
    );
  }

}
