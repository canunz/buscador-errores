package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.domain.ConteoTickets;

import cl.casol.backend.tickets.application.port.out.ConocimientoSugerido;
import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.FiltroTickets;
import cl.casol.backend.tickets.domain.PaginaTickets;
import cl.casol.backend.tickets.domain.exception.TicketNoEncontradoException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static cl.casol.backend.tickets.application.service.EscenarioTickets.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConsultarTicketsServiceTest {
    private final EscenarioTickets e = new EscenarioTickets();
    private final ConsultarTicketsService service = new ConsultarTicketsService(e.tickets, e.conocimiento, e.contexto);

    private FiltroTickets filtroEnviado() {
        ArgumentCaptor<FiltroTickets> filtro = ArgumentCaptor.forClass(FiltroTickets.class);
        verify(e.tickets).buscar(filtro.capture(), anyInt(), anyInt());
        return filtro.getValue();
    }

    @Test void usuarioSoloListaSusTicketsAunqueFiltrePorOtro() {
        when(e.tickets.buscar(any(), anyInt(), anyInt())).thenReturn(new PaginaTickets<>(List.of(), 0, 10, 0));
        service.listar(new FiltroTickets(EstadoTicket.ABIERTO, null, null, null, OTRO.id(), null), 0, 10, EMAIL_USUARIO);
        assertEquals(USUARIO.id(), filtroEnviado().solicitanteId());
    }

    @Test void soporteListaTodos() {
        when(e.tickets.buscar(any(), anyInt(), anyInt())).thenReturn(new PaginaTickets<>(List.of(), 0, 10, 0));
        service.listar(new FiltroTickets(null, null, null, null, null, "  impresora "), 0, 10, EMAIL_SOPORTE);
        FiltroTickets enviado = filtroEnviado();
        assertNull(enviado.solicitanteId());
        assertEquals("impresora", enviado.texto());
    }

    @Test void tamanoDePaginaSeAcota() {
        when(e.tickets.listarLigero(null, 100)).thenReturn(List.of());
        var resultado = service.listar(null, -3, 5000, EMAIL_ADMIN);
        verify(e.tickets).listarLigero(null, 100);
        assertEquals(0, resultado.pagina());
        assertEquals(100, resultado.tamano());
    }

    @Test void usuarioNoVeElDetalleDeUnTicketAjeno() {
        e.ticket(EstadoTicket.ABIERTO, null);
        assertThrows(TicketNoEncontradoException.class, () -> service.detalle(100L, EMAIL_OTRO));
        assertNotNull(service.detalle(100L, EMAIL_USUARIO));
        assertNotNull(service.detalle(100L, EMAIL_SOPORTE));
    }

    @Test void detalleOcultaComentariosInternosAlUsuario() {
        var ticket = e.ticket(EstadoTicket.EN_PROGRESO, SOPORTE.id());
        ticket.comentar(SOPORTE.id(), "interno", true, java.time.LocalDateTime.now());
        ticket.comentar(SOPORTE.id(), "público", false, java.time.LocalDateTime.now());
        assertEquals(1, service.detalle(100L, EMAIL_USUARIO).comentarios().size());
        assertEquals(2, service.detalle(100L, EMAIL_SOPORTE).comentarios().size());
    }

    @Test void sugerenciasUsanElBuscadorConLimite() {
        when(e.conocimiento.sugerir("impresora", 5)).thenReturn(List.of(new ConocimientoSugerido(1, "Impresora", null, null, null)));
        assertEquals(1, service.sugerencias(" impresora ").size());
        assertTrue(service.sugerencias("im").isEmpty());
        verify(e.conocimiento, times(1)).sugerir(anyString(), anyInt());
    }

    @Test void conteoDelUsuarioSoloCuentaSusTicketsYSoporteTodos() {
        ConteoTickets vacio = new ConteoTickets(java.util.Map.of(EstadoTicket.ABIERTO, 2L), 1, 1);
        when(e.tickets.contar(any())).thenReturn(vacio);
        service.conteo(EMAIL_USUARIO);
        verify(e.tickets).contar(USUARIO.id());
        assertEquals(2L, service.conteo(EMAIL_SOPORTE).total());
        verify(e.tickets).contar(null);
    }
}
