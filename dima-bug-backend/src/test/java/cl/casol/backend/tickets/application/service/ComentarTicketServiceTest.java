package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.exception.AccesoTicketDenegadoException;
import cl.casol.backend.tickets.domain.exception.TicketNoEncontradoException;
import cl.casol.backend.tickets.domain.exception.TransicionEstadoInvalidaException;
import org.junit.jupiter.api.Test;

import static cl.casol.backend.tickets.application.service.EscenarioTickets.*;
import static org.junit.jupiter.api.Assertions.*;

class ComentarTicketServiceTest {
    private final EscenarioTickets e = new EscenarioTickets();
    private final ComentarTicketService service = new ComentarTicketService(e.tickets, e.contexto);

    @Test void usuarioComentaSuTicket() {
        var ticket = e.ticket(EstadoTicket.EN_ESPERA, SOPORTE.id());
        service.comentar(100L, "Adjunto la foto", false, EMAIL_USUARIO);
        assertEquals(1, ticket.getComentarios().size());
    }

    @Test void usuarioNoPuedeDejarComentarioInterno() {
        e.ticket(EstadoTicket.EN_ESPERA, SOPORTE.id());
        assertThrows(AccesoTicketDenegadoException.class, () -> service.comentar(100L, "x", true, EMAIL_USUARIO));
    }

    @Test void soporteDejaComentarioInterno() {
        var ticket = e.ticket(EstadoTicket.EN_PROGRESO, SOPORTE.id());
        service.comentar(100L, "Revisar driver", true, EMAIL_SOPORTE);
        assertTrue(ticket.getComentarios().getFirst().interno());
    }

    @Test void usuarioNoComentaTicketAjeno() {
        e.ticket(EstadoTicket.ABIERTO, null);
        assertThrows(TicketNoEncontradoException.class, () -> service.comentar(100L, "x", false, EMAIL_OTRO));
    }

    @Test void ticketCerradoNoAdmiteComentarios() {
        e.ticket(EstadoTicket.CERRADO, SOPORTE.id());
        assertThrows(TransicionEstadoInvalidaException.class, () -> service.comentar(100L, "x", false, EMAIL_USUARIO));
    }

    @Test void administradorNoComenta() {
        e.ticket(EstadoTicket.EN_PROGRESO, SOPORTE.id());
        assertThrows(AccesoTicketDenegadoException.class, () -> service.comentar(100L, "x", false, EMAIL_ADMIN));
    }
}
