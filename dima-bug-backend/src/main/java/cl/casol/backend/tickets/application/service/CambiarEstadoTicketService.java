package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.application.port.in.CambiarEstadoTicketUseCase;
import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.application.port.out.TicketRepositoryPort;
import cl.casol.backend.tickets.domain.ActorTicket;
import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.Ticket;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import cl.casol.backend.tickets.domain.exception.TransicionEstadoInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Traduce el estado pedido a la transición del dominio y revisa quién puede hacerla:
 * <ul>
 *   <li>ABIERTO → EN_PROGRESO: soporte (queda asignado) o administrador.</li>
 *   <li>ABIERTO → CANCELADO: solicitante o administrador.</li>
 *   <li>EN_PROGRESO ↔ EN_ESPERA: soporte o administrador.</li>
 *   <li>RESUELTO → CERRADO: solicitante (confirma) o administrador.</li>
 *   <li>RESUELTO → EN_PROGRESO: solicitante (reabre) o administrador.</li>
 * </ul>
 */
@Service
@Transactional
public class CambiarEstadoTicketService implements CambiarEstadoTicketUseCase {
    private final TicketRepositoryPort tickets;
    private final ContextoTicketService contexto;

    public CambiarEstadoTicketService(TicketRepositoryPort tickets, ContextoTicketService contexto) {
        this.tickets = tickets;
        this.contexto = contexto;
    }

    @Override
    public TicketVista cambiarEstado(Long ticketId, EstadoTicket estadoNuevo, String motivo, String emailAutenticado) {
        if (estadoNuevo == null) throw new TicketInvalidoException("Debe indicar el estado nuevo");
        ActorTicket actor = contexto.actor(emailAutenticado);
        Ticket ticket = contexto.ticketVisible(ticketId, actor);
        LocalDateTime ahora = LocalDateTime.now();
        EstadoTicket actual = ticket.getEstado();

        switch (estadoNuevo) {
            case EN_PROGRESO -> pasarAEnProgreso(ticket, actor, motivo, ahora);
            case EN_ESPERA -> {
                contexto.exigirSoporte(actor, "dejar un ticket en espera");
                ticket.ponerEnEspera(actor.id(), motivo, ahora);
            }
            case CERRADO -> {
                contexto.exigirSolicitanteOTecnicoAsignado(actor, ticket, "cerrar el ticket");
                ticket.cerrar(actor.id(), motivo, ahora);
            }
            case CANCELADO -> {
                contexto.exigirSolicitante(actor, ticket, "cancelar el ticket");
                ticket.cancelar(actor.id(), motivo, ahora);
            }
            case RESUELTO -> throw new TicketInvalidoException(
                    "Para resolver un ticket usa la opción de resolución e indica la solución aplicada");
            case ABIERTO -> throw new TransicionEstadoInvalidaException(actual, EstadoTicket.ABIERTO);
        }
        return contexto.detalle(tickets.guardar(ticket), actor);
    }

    private void pasarAEnProgreso(Ticket ticket, ActorTicket actor, String motivo, LocalDateTime ahora) {
        switch (ticket.getEstado()) {
            case ABIERTO -> {
                contexto.exigirSoporte(actor, "tomar un ticket");
                // Soporte inicia el ticket al asignárselo.
                ticket.asignar(actor.id(), actor.id(), ahora);
            }
            case EN_ESPERA -> {
                contexto.exigirSoporte(actor, "retomar un ticket");
                ticket.iniciar(actor.id(), ahora);
            }
            case RESUELTO -> {
                contexto.exigirSolicitante(actor, ticket, "reabrir el ticket");
                ticket.reabrir(actor.id(), motivo, ahora);
            }
            default -> throw new TransicionEstadoInvalidaException(ticket.getEstado(), EstadoTicket.EN_PROGRESO);
        }
    }
}
