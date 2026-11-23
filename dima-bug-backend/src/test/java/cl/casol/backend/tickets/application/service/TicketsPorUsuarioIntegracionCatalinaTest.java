package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.domain.TicketsPorUsuario;
import cl.casol.backend.tickets.domain.exception.AccesoTicketDenegadoException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static cl.casol.backend.tickets.application.service.EscenarioTickets.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketsPorUsuarioIntegracionCatalinaTest {
    private final EscenarioTickets e = new EscenarioTickets();
    private final ConsultarTicketsService servicio = new ConsultarTicketsService(e.tickets, e.conocimiento, e.contexto);

    @Test void administradorConservaRankingYConteos() {
        var ranking = List.of(new TicketsPorUsuario(1, "Persona QA", "persona@example.invalid", 5, 3, 2));
        when(e.tickets.contarPorSolicitante()).thenReturn(ranking);
        assertEquals(ranking, servicio.ticketsPorUsuario(EMAIL_ADMIN));
        verify(e.tickets).contarPorSolicitante();
    }
    @Test void soporteNoAccedeACorreosNiConteosDelRanking() {
        assertThrows(AccesoTicketDenegadoException.class, () -> servicio.ticketsPorUsuario(EMAIL_SOPORTE));
        verify(e.tickets, never()).contarPorSolicitante();
    }
    @Test void usuarioNoAccedeAlRanking() {
        assertThrows(AccesoTicketDenegadoException.class, () -> servicio.ticketsPorUsuario(EMAIL_USUARIO));
        verify(e.tickets, never()).contarPorSolicitante();
    }
}
