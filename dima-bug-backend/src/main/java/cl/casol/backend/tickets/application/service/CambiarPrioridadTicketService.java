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

/**
 * Servicio de cambiar prioridad ticket (módulo de tickets).
 * Aquí van las reglas de negocio; para leer o guardar datos usa los puertos de salida.
 */
@Service
@Transactional
public class CambiarPrioridadTicketService implements CambiarPrioridadTicketUseCase {
    private final TicketRepositoryPort tickets;
    private final ContextoTicketService contexto;

    private final AvisoTicketCriticoPort avisoCritico;

    /** Sin aviso por correo (se usa en pruebas que no revisan el correo). */
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

    // Cambia prioridad.
    @Override
    public TicketVista cambiarPrioridad(Long ticketId, PrioridadTicket prioridad, String motivo, String emailAutenticado) {
        ActorTicket actor = contexto.actor(emailAutenticado);
        contexto.exigirStaff(actor, "cambiar la prioridad");
        Ticket ticket = contexto.ticketVisible(ticketId, actor);
        boolean eraCritico = ticket.getPrioridad() == PrioridadTicket.CRITICA;
        ticket.cambiarPrioridad(prioridad, actor.id(), motivo, LocalDateTime.now());
        Ticket guardado = tickets.guardar(ticket);

        // Si recién pasó a crítico, se avisa por correo a Soporte y al administrador
        if (!eraCritico && guardado.getPrioridad() == PrioridadTicket.CRITICA) {
            avisoCritico.avisar(guardado, actor.id());
        }
        return contexto.detalle(guardado, actor);
    }
}
