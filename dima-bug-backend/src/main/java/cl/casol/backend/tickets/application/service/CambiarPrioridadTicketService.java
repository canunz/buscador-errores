package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.application.port.in.CambiarPrioridadTicketUseCase;
import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.application.port.out.AvisoTicketCriticoPort;
import cl.casol.backend.tickets.application.port.out.TicketRepositoryPort;
import cl.casol.backend.tickets.domain.ActorTicket;
import cl.casol.backend.tickets.domain.PrioridadTicket;
import cl.casol.backend.tickets.domain.Ticket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class CambiarPrioridadTicketService implements CambiarPrioridadTicketUseCase {
    private final TicketRepositoryPort tickets;
    private final ContextoTicketService contexto;
    private final AvisoTicketCriticoPort avisoCritico;

    public CambiarPrioridadTicketService(TicketRepositoryPort tickets, ContextoTicketService contexto) {
        this(tickets, contexto, AvisoTicketCriticoPort.NINGUNO);
    }

    @Autowired
    public CambiarPrioridadTicketService(TicketRepositoryPort tickets, ContextoTicketService contexto,
            AvisoTicketCriticoPort avisoCritico) {
        this.tickets = tickets;
        this.contexto = contexto;
        this.avisoCritico = avisoCritico;
    }

    @Override
    public TicketVista cambiarPrioridad(Long ticketId, PrioridadTicket prioridad, String motivo, String emailAutenticado) {
        ActorTicket actor = contexto.actor(emailAutenticado);
        contexto.exigirStaff(actor, "cambiar la prioridad");
        Ticket ticket = contexto.ticketVisible(ticketId, actor);
        PrioridadTicket anterior = ticket.getPrioridad();
        ticket.cambiarPrioridad(prioridad, actor.id(), motivo, LocalDateTime.now());
        Ticket guardado = tickets.guardar(ticket);
        // Solo avisa cuando el ticket pasa a crítico, no cada vez que se guarda siendo crítico.
        if (anterior != PrioridadTicket.CRITICA && guardado.getPrioridad() == PrioridadTicket.CRITICA) {
            avisoCritico.avisar(guardado, actor.id());
        }
        return contexto.detalle(guardado, actor);
    }
}
