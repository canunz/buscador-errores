package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.application.port.out.BorradorConocimiento;
import cl.casol.backend.tickets.application.port.out.ConocimientoReferencia;
import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.exception.AccesoTicketDenegadoException;
import cl.casol.backend.tickets.domain.exception.ConocimientoYaVinculadoException;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import cl.casol.backend.tickets.domain.exception.TransicionEstadoInvalidaException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static cl.casol.backend.tickets.application.service.EscenarioTickets.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResolverTicketServiceTest {
    private final EscenarioTickets e = new EscenarioTickets();
    private final ResolverTicketService service = new ResolverTicketService(e.tickets, e.conocimiento, e.contexto);

    @Test void soporteResuelveVinculandoConocimientoExistente() {
        var ticket = e.ticket(EstadoTicket.EN_PROGRESO, SOPORTE.id());
        when(e.conocimiento.buscar(7)).thenReturn(Optional.of(new ConocimientoReferencia(7, "Tóner", "PUBLICADO")));
        service.resolver(100L, "Se cambió el tóner", 7, EMAIL_SOPORTE);
        assertEquals(EstadoTicket.RESUELTO, ticket.getEstado());
        assertEquals(7, ticket.getConocimientoId());
    }

    @Test void conocimientoInexistenteEs400() {
        e.ticket(EstadoTicket.EN_PROGRESO, SOPORTE.id());
        when(e.conocimiento.buscar(99)).thenReturn(Optional.empty());
        assertThrows(TicketInvalidoException.class, () -> service.resolver(100L, "Solución", 99, EMAIL_SOPORTE));
        verify(e.tickets, never()).guardar(any());
    }

    @Test void usuarioNoPuedeResolver() {
        e.ticket(EstadoTicket.EN_PROGRESO, SOPORTE.id());
        assertThrows(AccesoTicketDenegadoException.class, () -> service.resolver(100L, "x", null, EMAIL_USUARIO));
    }

    @Test void creaConocimientoBorradorPrellenadoYLoVincula() {
        var ticket = e.ticket(EstadoTicket.RESUELTO, SOPORTE.id());
        when(e.conocimiento.crearBorrador(any(), eq(EMAIL_SOPORTE))).thenReturn(55);

        service.crearConocimientoDesdeTicket(100L, EMAIL_SOPORTE);

        ArgumentCaptor<BorradorConocimiento> datos = ArgumentCaptor.forClass(BorradorConocimiento.class);
        verify(e.conocimiento).crearBorrador(datos.capture(), eq(EMAIL_SOPORTE));
        assertEquals("Impresora", datos.getValue().titulo());
        assertEquals("Se cambió el tóner", datos.getValue().solucion());
        assertEquals("TK-2026-0001", datos.getValue().codigoTicket());
        assertEquals(55, ticket.getConocimientoId());
    }

    @Test void noCreaConocimientoSiYaHayUnoVinculado() {
        var ticket = e.ticket(EstadoTicket.EN_PROGRESO, SOPORTE.id());
        ticket.vincularConocimiento(8, java.time.LocalDateTime.now());
        assertThrows(ConocimientoYaVinculadoException.class, () -> service.crearConocimientoDesdeTicket(100L, EMAIL_SOPORTE));
        verify(e.conocimiento, never()).crearBorrador(any(), any());
    }

    @Test void noCreaConocimientoDesdeTicketCerrado() {
        e.ticket(EstadoTicket.CERRADO, SOPORTE.id());
        assertThrows(TransicionEstadoInvalidaException.class, () -> service.crearConocimientoDesdeTicket(100L, EMAIL_SOPORTE));
        verify(e.conocimiento, never()).crearBorrador(any(), any());
    }
}
