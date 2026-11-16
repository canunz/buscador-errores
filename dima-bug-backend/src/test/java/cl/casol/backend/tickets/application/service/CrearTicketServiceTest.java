package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.application.port.in.CrearTicketUseCase;
import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.PrioridadTicket;
import cl.casol.backend.tickets.domain.Ticket;
import cl.casol.backend.tickets.domain.exception.AccesoTicketDenegadoException;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Year;
import java.util.Optional;

import static cl.casol.backend.tickets.application.service.EscenarioTickets.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CrearTicketServiceTest {
    private final EscenarioTickets e = new EscenarioTickets();
    private final CrearTicketService service = new CrearTicketService(e.tickets, e.catalogo, e.contexto);

    @Test void creaTicketAbiertoConCodigoCorrelativoYSolicitanteAutenticado() {
        int anio = Year.now().getValue();
        when(e.tickets.siguienteCorrelativo(anio)).thenReturn(7);
        when(e.catalogo.validarClasificacion(1, 2, 3)).thenReturn(Optional.empty());

        TicketVista vista = service.crear(new CrearTicketUseCase.Comando("Impresora", "No imprime",
                PrioridadTicket.ALTA, 1, 2, 3), EMAIL_USUARIO);

        ArgumentCaptor<Ticket> guardado = ArgumentCaptor.forClass(Ticket.class);
        verify(e.tickets).guardar(guardado.capture());
        assertEquals("TK-" + anio + "-0007", guardado.getValue().getCodigo());
        assertEquals(USUARIO.id(), guardado.getValue().getSolicitanteId());
        assertEquals(EstadoTicket.ABIERTO, vista.ticket().getEstado());
    }

    @Test void clasificacionInvalidaEs400YNoGuarda() {
        when(e.catalogo.validarClasificacion(1, 9, null)).thenReturn(Optional.of("El sistema no está asociado al hardware informado"));
        TicketInvalidoException ex = assertThrows(TicketInvalidoException.class, () -> service.crear(
                new CrearTicketUseCase.Comando("T", "D", null, 1, 9, null), EMAIL_USUARIO));
        assertTrue(ex.getMessage().contains("sistema"));
        verify(e.tickets, never()).guardar(any());
    }

    @Test void sinClasificacionNoConsultaCatalogos() {
        service.crear(new CrearTicketUseCase.Comando("T", "D", null, null, null, null), EMAIL_USUARIO);
        verify(e.catalogo, never()).validarClasificacion(any(), any(), any());
    }

    @Test void usuarioInexistenteOInactivoNoPuedeCrear() {
        when(e.usuarios.buscarPorEmail("x@dimarsa.cl")).thenReturn(Optional.empty());
        assertThrows(AccesoTicketDenegadoException.class, () -> service.crear(
                new CrearTicketUseCase.Comando("T", "D", null, null, null, null), "x@dimarsa.cl"));
    }

    @Test void administradorNoCreaTickets() {
        assertThrows(AccesoTicketDenegadoException.class, () -> service.crear(
                new CrearTicketUseCase.Comando("Impresora", "No imprime", PrioridadTicket.MEDIA, null, null, null), EMAIL_ADMIN));
        verify(e.tickets, never()).guardar(any());
    }
}
