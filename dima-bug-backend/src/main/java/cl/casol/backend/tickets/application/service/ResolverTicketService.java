package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.application.port.in.ResolverTicketUseCase;
import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.application.port.out.BorradorConocimiento;
import cl.casol.backend.tickets.application.port.out.ConocimientoPort;
import cl.casol.backend.tickets.application.port.out.TicketRepositoryPort;
import cl.casol.backend.tickets.domain.ActorTicket;
import cl.casol.backend.tickets.domain.Ticket;
import cl.casol.backend.tickets.domain.exception.ConocimientoYaVinculadoException;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class ResolverTicketService implements ResolverTicketUseCase {
    private final TicketRepositoryPort tickets;
    private final ConocimientoPort conocimiento;
    private final ContextoTicketService contexto;

    public ResolverTicketService(TicketRepositoryPort tickets, ConocimientoPort conocimiento,
            ContextoTicketService contexto) {
        this.tickets = tickets;
        this.conocimiento = conocimiento;
        this.contexto = contexto;
    }

    @Override
    public TicketVista resolver(Long ticketId, String solucion, Integer conocimientoId, String emailAutenticado) {
        ActorTicket actor = contexto.actor(emailAutenticado);
        contexto.exigirSoporte(actor, "resolver tickets");
        Ticket ticket = contexto.ticketVisible(ticketId, actor);
        if (conocimientoId != null && conocimiento.buscar(conocimientoId).isEmpty()) {
            throw new TicketInvalidoException("El conocimiento indicado no existe");
        }
        ticket.resolver(solucion, conocimientoId, actor.id(), LocalDateTime.now());
        return contexto.detalle(tickets.guardar(ticket), actor);
    }

    @Override
    public TicketVista crearConocimientoDesdeTicket(Long ticketId, String emailAutenticado) {
        ActorTicket actor = contexto.actor(emailAutenticado);
        contexto.exigirSoporte(actor, "crear conocimiento desde un ticket");
        Ticket ticket = contexto.ticketVisible(ticketId, actor);
        ticket.validarAdmiteCambios();
        if (ticket.getConocimientoId() != null) throw new ConocimientoYaVinculadoException();

        Integer nuevoId = conocimiento.crearBorrador(new BorradorConocimiento(ticket.getTitulo(),
                ticket.getDescripcion(), ticket.getHardwareId(), ticket.getSistemaId(), ticket.getModuloId(),
                ticket.getSolucion(), ticket.getCodigo()), emailAutenticado);
        ticket.vincularConocimiento(nuevoId, LocalDateTime.now());
        return contexto.detalle(tickets.guardar(ticket), actor);
    }
}
