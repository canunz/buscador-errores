package cl.casol.backend.tickets.domain;

import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EtiquetaTest {
    @Test void normalizaNombreYColor() {
        Etiqueta e = Etiqueta.nueva("  impresoras ", "#e53935");
        assertEquals("impresoras", e.getNombre());
        assertEquals("#E53935", e.getColor());
        assertTrue(e.isActiva());
        assertEquals(NivelEtiqueta.NORMAL, e.getNivel());
    }

    @Test void guardaElNivelYSinNivelQuedaNormal() {
        assertEquals(NivelEtiqueta.CRITICA, Etiqueta.nueva("caida", "#DC2626", NivelEtiqueta.CRITICA).getNivel());
        Etiqueta e = Etiqueta.nueva("red", "#1E4FD6", NivelEtiqueta.IMPORTANTE);
        e.editar("red", "#1E4FD6", null);
        assertEquals(NivelEtiqueta.NORMAL, e.getNivel());
    }

    @Test void rechazaColorONombreInvalido() {
        assertThrows(TicketInvalidoException.class, () -> Etiqueta.nueva("red", "rojo"));
        assertThrows(TicketInvalidoException.class, () -> Etiqueta.nueva("red", "#12345"));
        assertThrows(TicketInvalidoException.class, () -> Etiqueta.nueva(" ", "#123456"));
        assertThrows(TicketInvalidoException.class, () -> Etiqueta.nueva("x".repeat(41), "#123456"));
    }
}
