package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.PrioridadTicket;
import cl.casol.backend.tickets.domain.exception.AccesoTicketDenegadoException;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import cl.casol.backend.tickets.domain.exception.TransicionEstadoInvalidaException;
import org.junit.jupiter.api.Test;

import static cl.casol.backend.tickets.application.service.EscenarioTickets.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CambiarPrioridadTicketServiceTest {
    private final EscenarioTickets e = new EscenarioTickets();
    private final CambiarPrioridadTicketService service = new CambiarPrioridadTicketService(e.tickets, e.contexto);

    @Test void soporteSubeLaPrioridadYQuedaEnElHistorial() {
        var ticket = e.ticket(EstadoTicket.EN_PROGRESO, SOPORTE.id());
        service.cambiarPrioridad(100L, PrioridadTicket.CRITICA, "Afecta a toda la sucursal", EMAIL_SOPORTE);
        assertEquals(PrioridadTicket.CRITICA, ticket.getPrioridad());
        var ultimo = ticket.getHistorial().getLast();
        assertEquals(EstadoTicket.EN_PROGRESO, ultimo.estadoNuevo());
        assertEquals(EstadoTicket.EN_PROGRESO, ultimo.estadoAnterior());
        assertEquals("Prioridad: MEDIA -> CRITICA. Afecta a toda la sucursal", ultimo.motivo());
        verify(e.tickets).guardar(ticket);
    }

    @Test void elAdministradorTambienPuedeCambiarla() {
        var ticket = e.ticket(EstadoTicket.ABIERTO, null);
        service.cambiarPrioridad(100L, PrioridadTicket.BAJA, null, EMAIL_ADMIN);
        assertEquals(PrioridadTicket.BAJA, ticket.getPrioridad());
        assertEquals("Prioridad: MEDIA -> BAJA", ticket.getHistorial().getLast().motivo());
    }

    @Test void elUsuarioNoPuedeCambiarla() {
        e.ticket(EstadoTicket.ABIERTO, null);
        assertThrows(AccesoTicketDenegadoException.class,
                () -> service.cambiarPrioridad(100L, PrioridadTicket.CRITICA, null, EMAIL_USUARIO));
        verify(e.tickets, never()).guardar(any());
    }

    @Test void mismaPrioridadEs400() {
        e.ticket(EstadoTicket.ABIERTO, null);
        assertThrows(TicketInvalidoException.class,
                () -> service.cambiarPrioridad(100L, PrioridadTicket.MEDIA, null, EMAIL_SOPORTE));
    }

    @Test void ticketCerradoNoSeModifica() {
        e.ticket(EstadoTicket.CERRADO, SOPORTE.id());
        assertThrows(TransicionEstadoInvalidaException.class,
                () -> service.cambiarPrioridad(100L, PrioridadTicket.ALTA, null, EMAIL_SOPORTE));
        verify(e.tickets, never()).guardar(any());
    }

    @Test void motivoMuyLargoEs400() {
        e.ticket(EstadoTicket.ABIERTO, null);
        assertThrows(TicketInvalidoException.class,
                () -> service.cambiarPrioridad(100L, PrioridadTicket.ALTA, "x".repeat(181), EMAIL_SOPORTE));
    }
}
