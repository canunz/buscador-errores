package cl.casol.backend.tickets.api;

import cl.casol.backend.tickets.application.port.in.CrearTicketUseCase;
import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.domain.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketsFachadaTest {
    @Test void delegaAlCasoDeUsoExistenteConActorYPrioridadMedia() {
        var caso = mock(CrearTicketUseCase.class);
        var ticket = Ticket.crear("TK-2026-0001", "Error POS", "Consulta QA", PrioridadTicket.MEDIA,
                9, null, null, null, LocalDateTime.of(2026,11,22,12,0));
        when(caso.crear(any(), eq("servicio@example.invalid"))).thenReturn(
                new TicketVista(ticket, List.of(), Map.of(), null, null, List.of(), List.of()));
        assertEquals("TK-2026-0001", new TicketsFachada(caso).crear(
                new TicketsFachada.Solicitud("Error POS", "Consulta QA"), "servicio@example.invalid"));
        var comando = ArgumentCaptor.forClass(CrearTicketUseCase.Comando.class);
        verify(caso).crear(comando.capture(), eq("servicio@example.invalid"));
        assertEquals("Error POS", comando.getValue().titulo());
        assertEquals("Consulta QA", comando.getValue().descripcion());
        assertEquals(PrioridadTicket.MEDIA, comando.getValue().prioridad());
        assertNull(comando.getValue().hardwareId());
        assertNull(comando.getValue().sistemaId());
        assertNull(comando.getValue().moduloId());
    }
    @Test void noOcultaNiEvitaLosRechazosDelCasoDeUso() {
        var caso = mock(CrearTicketUseCase.class);
        var rechazo = new IllegalArgumentException("Actor no autorizado");
        when(caso.crear(any(), anyString())).thenThrow(rechazo);
        assertSame(rechazo, assertThrows(IllegalArgumentException.class, () -> new TicketsFachada(caso).crear(
                new TicketsFachada.Solicitud("Error POS", "Consulta QA"), "sin-acceso@example.invalid")));
    }
}
