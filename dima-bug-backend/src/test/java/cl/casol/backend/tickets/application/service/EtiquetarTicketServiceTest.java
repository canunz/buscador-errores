package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.Etiqueta;
import cl.casol.backend.tickets.domain.exception.AccesoTicketDenegadoException;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static cl.casol.backend.tickets.application.service.EscenarioTickets.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EtiquetarTicketServiceTest {
    private final EscenarioTickets e = new EscenarioTickets();
    private final EtiquetarTicketService service = new EtiquetarTicketService(e.tickets, e.etiquetas, e.contexto);

    @Test void soporteReemplazaEtiquetas() {
        var ticket = e.ticket(EstadoTicket.EN_PROGRESO, SOPORTE.id());
        when(e.etiquetas.buscarPorIds(any())).thenReturn(List.of(new Etiqueta(1L, "red", "#1E4FD6", true),
                new Etiqueta(2L, "impresoras", "#E53935", true)));
        service.etiquetar(100L, Set.of(1L, 2L), EMAIL_SOPORTE);
        assertEquals(Set.of(1L, 2L), ticket.getEtiquetaIds());
    }

    @Test void etiquetaInexistenteEs400() {
        e.ticket(EstadoTicket.EN_PROGRESO, SOPORTE.id());
        when(e.etiquetas.buscarPorIds(any())).thenReturn(List.of());
        assertThrows(TicketInvalidoException.class, () -> service.etiquetar(100L, Set.of(9L), EMAIL_SOPORTE));
    }

    @Test void noSeAgreganEtiquetasDesactivadas() {
        e.ticket(EstadoTicket.EN_PROGRESO, SOPORTE.id());
        when(e.etiquetas.buscarPorIds(any())).thenReturn(List.of(new Etiqueta(3L, "vieja", "#000000", false)));
        assertThrows(TicketInvalidoException.class, () -> service.etiquetar(100L, Set.of(3L), EMAIL_SOPORTE));
    }

    @Test void usuarioNoPuedeEtiquetar() {
        e.ticket(EstadoTicket.ABIERTO, null);
        assertThrows(AccesoTicketDenegadoException.class, () -> service.etiquetar(100L, Set.of(), EMAIL_USUARIO));
    }
}
