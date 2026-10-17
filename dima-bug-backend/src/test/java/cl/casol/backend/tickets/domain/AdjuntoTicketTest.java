package cl.casol.backend.tickets.domain;

import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdjuntoTicketTest {
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    static final byte[] MP4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};

    @Test void reconoceImagenesVideosYPdf() {
        assertEquals("image/png", FormatoAdjunto.mime(PNG));
        assertEquals("video/mp4", FormatoAdjunto.mime(MP4));
        assertEquals("application/pdf", FormatoAdjunto.mime("%PDF-1.7".getBytes()));
        assertEquals(TipoAdjunto.VIDEO, FormatoAdjunto.tipo("video/mp4"));
        assertEquals(TipoAdjunto.IMAGEN, FormatoAdjunto.tipo("image/png"));
    }

    @Test void rechazaFormatosNoPermitidosYVacios() {
        assertThrows(TicketInvalidoException.class, () -> AdjuntoTicket.validarContenido("MZ ejecutable".getBytes()));
        assertThrows(TicketInvalidoException.class, () -> AdjuntoTicket.validarContenido(new byte[0]));
    }

    @Test void limpiaElNombreDelArchivo() {
        AdjuntoTicket a = AdjuntoTicket.nuevo(1L, null, "C:\\fotos\\error.png", PNG, "ref", 1, LocalDateTime.now());
        assertEquals("error.png", a.nombre());
        assertEquals(PNG.length, a.tamano());
        assertEquals("archivo.png", AdjuntoTicket.nuevo(1L, null, "  ", PNG, "ref", 1, LocalDateTime.now()).nombre());
    }

    @Test void losDeNotasInternasSoloLosVeSoporte() {
        LocalDateTime ahora = LocalDateTime.now();
        Ticket ticket = new Ticket(1L, "TK-2026-0001", "t", "d", EstadoTicket.EN_PROGRESO, PrioridadTicket.MEDIA, 1, 3,
                null, null, null, null, null, ahora, ahora, null, null, Set.of(),
                List.of(new ComentarioTicket(8L, 3, "nota", true, ahora)), List.of());
        AdjuntoTicket interno = new AdjuntoTicket(1L, 1L, 8L, "x.png", "image/png", 10, "r", 3, ahora);
        AdjuntoTicket general = new AdjuntoTicket(2L, 1L, null, "y.png", "image/png", 10, "r2", 1, ahora);
        ActorTicket usuario = new ActorTicket(1, "Ana", RolTicket.USUARIO);
        ActorTicket soporte = new ActorTicket(3, "Tito", RolTicket.SOPORTE);
        assertFalse(interno.visiblePara(usuario, ticket));
        assertTrue(interno.visiblePara(soporte, ticket));
        assertTrue(general.visiblePara(usuario, ticket));
    }
}
