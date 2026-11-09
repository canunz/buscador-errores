package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.application.port.in.CambiarPrioridadTicketUseCase;
import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.application.port.out.TicketRepositoryPort;
import cl.casol.backend.tickets.domain.ActorTicket;
import cl.casol.backend.tickets.domain.PrioridadTicket;
import cl.casol.backend.tickets.domain.Ticket;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class CambiarPrioridadTicketService implements CambiarPrioridadTicketUseCase {
    private final TicketRepositoryPort tickets;
    private final ContextoTicketService contexto;

    public CambiarPrioridadTicketService(TicketRepositoryPort tickets, ContextoTicketService contexto) {
        this.tickets = tickets;
        this.contexto = contexto;
    }

    @Override
    public TicketVista cambiarPrioridad(Long ticketId, PrioridadTicket prioridad, String motivo, String emailAutenticado) {
        ActorTicket actor = contexto.actor(emailAutenticado);
        contexto.exigirStaff(actor, "cambiar la prioridad");
        Ticket ticket = contexto.ticketVisible(ticketId, actor);
        ticket.cambiarPrioridad(prioridad, actor.id(), motivo, LocalDateTime.now());
        return contexto.detalle(tickets.guardar(ticket), actor);
    }
}
