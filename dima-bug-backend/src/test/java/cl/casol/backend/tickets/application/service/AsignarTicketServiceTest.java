package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.exception.AccesoTicketDenegadoException;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import cl.casol.backend.tickets.domain.exception.TransicionEstadoInvalidaException;
import org.junit.jupiter.api.Test;

import static cl.casol.backend.tickets.application.service.EscenarioTickets.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AsignarTicketServiceTest {
    private final EscenarioTickets e = new EscenarioTickets();
    private final AsignarTicketService service = new AsignarTicketService(e.tickets, e.usuarios, e.contexto);

    @Test void soporteSeAsignaASiMismoYElTicketPasaAEnProgreso() {
        var ticket = e.ticket(EstadoTicket.ABIERTO, null);
        service.asignar(100L, SOPORTE.id(), EMAIL_SOPORTE);
        assertEquals(SOPORTE.id(), ticket.getAsignadoId());
        assertEquals(EstadoTicket.EN_PROGRESO, ticket.getEstado());
        verify(e.tickets).guardar(ticket);
    }

    @Test void soporteNoPuedeAsignarAOtro() {
        e.ticket(EstadoTicket.ABIERTO, null);
        assertThrows(AccesoTicketDenegadoException.class, () -> service.asignar(100L, SOPORTE_2.id(), EMAIL_SOPORTE));
        verify(e.tickets, never()).guardar(any());
    }

    @Test void otroTecnicoPuedeAsignarseloASiMismo() {
        var ticket = e.ticket(EstadoTicket.EN_PROGRESO, SOPORTE.id());
        service.asignar(100L, SOPORTE_2.id(), "s2@dimarsa.cl");
        assertEquals(SOPORTE_2.id(), ticket.getAsignadoId());
    }

    @Test void adminAsignaUnTicketSinTecnico() {
        var ticket = e.ticket(EstadoTicket.ABIERTO, null);
        service.asignar(100L, SOPORTE.id(), EMAIL_ADMIN);
        assertEquals(SOPORTE.id(), ticket.getAsignadoId());
        assertEquals(EstadoTicket.EN_PROGRESO, ticket.getEstado());
    }

    @Test void adminReasignaAOtroTecnico() {
        var ticket = e.ticket(EstadoTicket.EN_PROGRESO, SOPORTE.id());
        service.asignar(100L, SOPORTE_2.id(), EMAIL_ADMIN);
        assertEquals(SOPORTE_2.id(), ticket.getAsignadoId());
    }

    @Test void adminSoloAsignaATecnicosDeSoporte() {
        e.ticket(EstadoTicket.ABIERTO, null);
        assertThrows(TicketInvalidoException.class, () -> service.asignar(100L, OTRO.id(), EMAIL_ADMIN));
        assertThrows(TicketInvalidoException.class, () -> service.asignar(100L, ADMIN.id(), EMAIL_ADMIN));
        verify(e.tickets, never()).guardar(any());
    }

    @Test void usuarioNoPuedeAsignar() {
        e.ticket(EstadoTicket.ABIERTO, null);
        assertThrows(AccesoTicketDenegadoException.class, () -> service.asignar(100L, USUARIO.id(), EMAIL_USUARIO));
    }

    @Test void noSeAsignaUnTicketCerrado() {
        e.ticket(EstadoTicket.CERRADO, SOPORTE.id());
        assertThrows(TransicionEstadoInvalidaException.class, () -> service.asignar(100L, SOPORTE.id(), EMAIL_SOPORTE));
    }
}
