package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.exception.AccesoTicketDenegadoException;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import cl.casol.backend.tickets.domain.exception.TicketNoEncontradoException;
import cl.casol.backend.tickets.domain.exception.TransicionEstadoInvalidaException;
import org.junit.jupiter.api.Test;

import static cl.casol.backend.tickets.application.service.EscenarioTickets.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CambiarEstadoTicketServiceTest {
    private final EscenarioTickets e = new EscenarioTickets();
    private final CambiarEstadoTicketService service = new CambiarEstadoTicketService(e.tickets, e.contexto);

    @Test void soporteTomaTicketAbiertoYQuedaAsignado() {
        var ticket = e.ticket(EstadoTicket.ABIERTO, null);
        service.cambiarEstado(100L, EstadoTicket.EN_PROGRESO, null, EMAIL_SOPORTE);
        assertEquals(EstadoTicket.EN_PROGRESO, ticket.getEstado());
        assertEquals(SOPORTE.id(), ticket.getAsignadoId());
    }

    @Test void usuarioNoPuedeIniciarSuTicket() {
        e.ticket(EstadoTicket.ABIERTO, null);
        assertThrows(AccesoTicketDenegadoException.class,
                () -> service.cambiarEstado(100L, EstadoTicket.EN_PROGRESO, null, EMAIL_USUARIO));
    }

    @Test void solicitanteCancelaSuTicketAbierto() {
        var ticket = e.ticket(EstadoTicket.ABIERTO, null);
        service.cambiarEstado(100L, EstadoTicket.CANCELADO, "Ya funciona", EMAIL_USUARIO);
        assertEquals(EstadoTicket.CANCELADO, ticket.getEstado());
    }

    @Test void soporteQueNoEsSolicitanteNoPuedeCancelar() {
        e.ticket(EstadoTicket.ABIERTO, null);
        assertThrows(AccesoTicketDenegadoException.class,
                () -> service.cambiarEstado(100L, EstadoTicket.CANCELADO, null, EMAIL_SOPORTE));
    }

    @Test void adminNoCancelaNiCierraNiReabre() {
        e.ticket(EstadoTicket.ABIERTO, null);
        assertThrows(AccesoTicketDenegadoException.class,
                () -> service.cambiarEstado(100L, EstadoTicket.CANCELADO, null, EMAIL_ADMIN));
        e.ticket(EstadoTicket.RESUELTO, SOPORTE.id());
        assertThrows(AccesoTicketDenegadoException.class,
                () -> service.cambiarEstado(100L, EstadoTicket.CERRADO, null, EMAIL_ADMIN));
        assertThrows(AccesoTicketDenegadoException.class,
                () -> service.cambiarEstado(100L, EstadoTicket.EN_PROGRESO, "x", EMAIL_ADMIN));
        verify(e.tickets, never()).guardar(any());
    }

    @Test void adminNoTomaNiPoneEnEspera() {
        e.ticket(EstadoTicket.ABIERTO, null);
        assertThrows(AccesoTicketDenegadoException.class,
                () -> service.cambiarEstado(100L, EstadoTicket.EN_PROGRESO, null, EMAIL_ADMIN));
        e.ticket(EstadoTicket.EN_PROGRESO, SOPORTE.id());
        assertThrows(AccesoTicketDenegadoException.class,
                () -> service.cambiarEstado(100L, EstadoTicket.EN_ESPERA, null, EMAIL_ADMIN));
    }

    @Test void soportePoneEnEsperaYRetoma() {
        var ticket = e.ticket(EstadoTicket.EN_PROGRESO, SOPORTE.id());
        service.cambiarEstado(100L, EstadoTicket.EN_ESPERA, "Esperando respuesta", EMAIL_SOPORTE);
        assertEquals(EstadoTicket.EN_ESPERA, ticket.getEstado());
        service.cambiarEstado(100L, EstadoTicket.EN_PROGRESO, null, EMAIL_SOPORTE);
        assertEquals(EstadoTicket.EN_PROGRESO, ticket.getEstado());
    }

    @Test void usuarioNoPuedePonerEnEspera() {
        e.ticket(EstadoTicket.EN_PROGRESO, SOPORTE.id());
        assertThrows(AccesoTicketDenegadoException.class,
                () -> service.cambiarEstado(100L, EstadoTicket.EN_ESPERA, null, EMAIL_USUARIO));
    }

    @Test void solicitanteConfirmaYCierra() {
        var ticket = e.ticket(EstadoTicket.RESUELTO, SOPORTE.id());
        service.cambiarEstado(100L, EstadoTicket.CERRADO, null, EMAIL_USUARIO);
        assertEquals(EstadoTicket.CERRADO, ticket.getEstado());
    }

    @Test void elTecnicoACargoPuedeCerrarSiElUsuarioNoResponde() {
        var ticket = e.ticket(EstadoTicket.RESUELTO, SOPORTE.id());
        service.cambiarEstado(100L, EstadoTicket.CERRADO, "Sin respuesta del usuario", EMAIL_SOPORTE);
        assertEquals(EstadoTicket.CERRADO, ticket.getEstado());
    }

    @Test void otroTecnicoNoPuedeCerrarNiReabrir() {
        e.ticket(EstadoTicket.RESUELTO, SOPORTE.id());
        assertThrows(AccesoTicketDenegadoException.class,
                () -> service.cambiarEstado(100L, EstadoTicket.CERRADO, null, "s2@dimarsa.cl"));
        assertThrows(AccesoTicketDenegadoException.class,
                () -> service.cambiarEstado(100L, EstadoTicket.EN_PROGRESO, "x", EMAIL_SOPORTE));
    }

    @Test void solicitanteReabreSiNoSeSoluciono() {
        var ticket = e.ticket(EstadoTicket.RESUELTO, SOPORTE.id());
        service.cambiarEstado(100L, EstadoTicket.EN_PROGRESO, "Sigue fallando", EMAIL_USUARIO);
        assertEquals(EstadoTicket.EN_PROGRESO, ticket.getEstado());
        assertEquals("Sigue fallando", ticket.getHistorial().getLast().motivo());
    }

    @Test void resolverPorEstaRutaEs400() {
        e.ticket(EstadoTicket.EN_PROGRESO, SOPORTE.id());
        assertThrows(TicketInvalidoException.class,
                () -> service.cambiarEstado(100L, EstadoTicket.RESUELTO, null, EMAIL_SOPORTE));
    }

    @Test void transicionNoPermitidaEs409YNoGuarda() {
        e.ticket(EstadoTicket.CERRADO, SOPORTE.id());
        assertThrows(TransicionEstadoInvalidaException.class,
                () -> service.cambiarEstado(100L, EstadoTicket.EN_PROGRESO, null, EMAIL_SOPORTE));
        assertThrows(TransicionEstadoInvalidaException.class,
                () -> service.cambiarEstado(100L, EstadoTicket.ABIERTO, null, EMAIL_SOPORTE));
        verify(e.tickets, never()).guardar(any());
    }

    @Test void usuarioNoPuedeTocarTicketsAjenos() {
        e.ticket(EstadoTicket.ABIERTO, null);
        assertThrows(TicketNoEncontradoException.class,
                () -> service.cambiarEstado(100L, EstadoTicket.CANCELADO, null, EMAIL_OTRO));
    }
}
