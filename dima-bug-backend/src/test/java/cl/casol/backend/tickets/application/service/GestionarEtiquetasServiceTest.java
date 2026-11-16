package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.domain.Etiqueta;
import cl.casol.backend.tickets.domain.NivelEtiqueta;
import cl.casol.backend.tickets.domain.exception.AccesoTicketDenegadoException;
import cl.casol.backend.tickets.domain.exception.EtiquetaDuplicadaException;
import cl.casol.backend.tickets.domain.exception.EtiquetaNoEncontradaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static cl.casol.backend.tickets.application.service.EscenarioTickets.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GestionarEtiquetasServiceTest {
    private final EscenarioTickets e = new EscenarioTickets();
    private final GestionarEtiquetasService service = new GestionarEtiquetasService(e.etiquetas, e.contexto);

    @BeforeEach void setUp() {
        when(e.etiquetas.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void soporteCreaEtiqueta() {
        Etiqueta creada = service.crear("impresoras", "#e53935", NivelEtiqueta.CRITICA, EMAIL_SOPORTE);
        assertEquals("#E53935", creada.getColor());
        assertEquals(NivelEtiqueta.CRITICA, creada.getNivel());
    }

    @Test void nombreRepetidoEs409() {
        when(e.etiquetas.existeNombre("red", null)).thenReturn(true);
        assertThrows(EtiquetaDuplicadaException.class, () -> service.crear("red", "#000000", null, EMAIL_SOPORTE));
    }

    @Test void soloSoporteGestiona() {
        assertThrows(AccesoTicketDenegadoException.class, () -> service.crear("red", "#000000", null, EMAIL_ADMIN));
        assertThrows(AccesoTicketDenegadoException.class, () -> service.crear("red", "#000000", null, EMAIL_USUARIO));
        assertThrows(AccesoTicketDenegadoException.class, () -> service.cambiarEstado(1L, false, EMAIL_USUARIO));
    }

    @Test void editarValidaDuplicadoExcluyendoLaMisma() {
        when(e.etiquetas.buscarPorId(1L)).thenReturn(Optional.of(new Etiqueta(1L, "red", "#000000", true)));
        when(e.etiquetas.existeNombre("redes", 1L)).thenReturn(true);
        assertThrows(EtiquetaDuplicadaException.class, () -> service.editar(1L, "redes", "#111111", null, EMAIL_SOPORTE));
    }

    @Test void desactivarEtiquetaInexistenteEs404() {
        when(e.etiquetas.buscarPorId(9L)).thenReturn(Optional.empty());
        assertThrows(EtiquetaNoEncontradaException.class, () -> service.cambiarEstado(9L, false, EMAIL_SOPORTE));
    }

    @Test void soloSoporteVeInactivas() {
        service.listar(true, EMAIL_USUARIO);
        verify(e.etiquetas).listar(false);
        service.listar(true, EMAIL_SOPORTE);
        verify(e.etiquetas).listar(true);
    }
}
