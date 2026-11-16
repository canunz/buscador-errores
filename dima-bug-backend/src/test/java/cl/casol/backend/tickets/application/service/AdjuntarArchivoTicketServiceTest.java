package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.domain.AdjuntoTicket;
import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.exception.AdjuntoNoEncontradoException;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import cl.casol.backend.tickets.domain.exception.TicketNoEncontradoException;
import cl.casol.backend.tickets.domain.exception.TransicionEstadoInvalidaException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static cl.casol.backend.tickets.application.service.EscenarioTickets.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdjuntarArchivoTicketServiceTest {
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private final EscenarioTickets e = new EscenarioTickets();
    private final AdjuntarArchivoTicketService service = new AdjuntarArchivoTicketService(e.contexto, e.adjuntos, e.almacen);

    @Test void solicitanteAdjuntaUnaImagen() {
        e.ticket(EstadoTicket.ABIERTO, null);
        when(e.almacen.guardar(any())).thenReturn("ref-1");
        when(e.adjuntos.guardar(any())).thenAnswer(i -> i.getArgument(0));
        when(e.adjuntos.listarPorTicket(100L)).thenReturn(List.of());
        service.adjuntar(100L, null, "pantalla.png", PNG, EMAIL_USUARIO);
        verify(e.almacen).guardar(PNG);
        verify(e.adjuntos).guardar(any());
    }

    @Test void formatoNoPermitidoNoSeGuardaEnDisco() {
        e.ticket(EstadoTicket.ABIERTO, null);
        assertThrows(TicketInvalidoException.class,
                () -> service.adjuntar(100L, null, "virus.exe", "MZ".getBytes(), EMAIL_USUARIO));
        verifyNoInteractions(e.almacen);
    }

    @Test void otroUsuarioNoPuedeAdjuntarNiVer() {
        e.ticket(EstadoTicket.ABIERTO, null);
        assertThrows(TicketNoEncontradoException.class, () -> service.adjuntar(100L, null, "a.png", PNG, EMAIL_OTRO));
        assertThrows(TicketNoEncontradoException.class, () -> service.descargar(100L, 1L, EMAIL_OTRO));
    }

    @Test void ticketCerradoNoAdmiteArchivos() {
        e.ticket(EstadoTicket.CERRADO, 3);
        assertThrows(TransicionEstadoInvalidaException.class,
                () -> service.adjuntar(100L, null, "a.png", PNG, EMAIL_USUARIO));
    }

    @Test void soloEnTusPropiosMensajes() {
        e.ticket(EstadoTicket.EN_PROGRESO, 3);
        assertThrows(TicketInvalidoException.class, () -> service.adjuntar(100L, 55L, "a.png", PNG, EMAIL_USUARIO));
    }

    @Test void siFallaLaBaseSeBorraElArchivo() {
        e.ticket(EstadoTicket.ABIERTO, null);
        when(e.almacen.guardar(any())).thenReturn("ref-1");
        when(e.adjuntos.guardar(any())).thenThrow(new IllegalStateException("bd caída"));
        assertThrows(IllegalStateException.class, () -> service.adjuntar(100L, null, "a.png", PNG, EMAIL_USUARIO));
        verify(e.almacen).eliminar("ref-1");
    }

    @Test void descargaDevuelveElContenido() {
        e.ticket(EstadoTicket.EN_PROGRESO, 3);
        when(e.adjuntos.buscar(100L, 1L)).thenReturn(Optional.of(
                new AdjuntoTicket(1L, 100L, null, "a.png", "image/png", PNG.length, "ref-1", 1, LocalDateTime.now())));
        when(e.almacen.leer("ref-1")).thenReturn(PNG);
        assertArrayEquals(PNG, service.descargar(100L, 1L, EMAIL_SOPORTE).contenido());
        assertThrows(AdjuntoNoEncontradoException.class, () -> service.descargar(100L, 2L, EMAIL_SOPORTE));
    }
}
