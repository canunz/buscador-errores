package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.application.port.in.ComentarTicketUseCase;
import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.application.port.out.TicketRepositoryPort;
import cl.casol.backend.tickets.domain.ActorTicket;
import cl.casol.backend.tickets.domain.Ticket;
import cl.casol.backend.tickets.domain.exception.AccesoTicketDenegadoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class ComentarTicketService implements ComentarTicketUseCase {
    private final TicketRepositoryPort tickets;
    private final ContextoTicketService contexto;

    public ComentarTicketService(TicketRepositoryPort tickets, ContextoTicketService contexto) {
        this.tickets = tickets;
        this.contexto = contexto;
    }

    @Override
    public TicketVista comentar(Long ticketId, String texto, boolean interno, String emailAutenticado) {
        ActorTicket actor = contexto.actor(emailAutenticado);
        if (actor.esAdministrador()) {
            throw new AccesoTicketDenegadoException("El administrador supervisa los tickets, pero no participa en la conversación");
        }
        if (interno && !actor.esStaff()) {
            throw new AccesoTicketDenegadoException("Solo soporte puede dejar comentarios internos");
        }
        Ticket ticket = contexto.ticketVisible(ticketId, actor);
        ticket.comentar(actor.id(), texto, interno, LocalDateTime.now());
        return contexto.detalle(tickets.guardar(ticket), actor);
    }
}
