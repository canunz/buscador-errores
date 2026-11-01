package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsultarChatbotServiceTest {
    private final BusquedaConocimientoRepository busqueda = mock(BusquedaConocimientoRepository.class);
    private final SolucionRepository soluciones = mock(SolucionRepository.class);
    private final ConsultarChatbotService servicio = new ConsultarChatbotService(new BuscarConocimientoService(busqueda), soluciones);

    private ResultadoBusquedaConocimiento conocimiento(int id) {
        return new ResultadoBusquedaConocimiento(id, "Impresora", null, null, null, null, null, null, null, null, 1d);
    }

    @Test void usaBusquedaAutorizadaYRecortaConsulta() {
        when(busqueda.buscarAutoservicio("impresora")).thenReturn(List.of(conocimiento(1)));
        when(soluciones.buscarActivasAutoservicioPorConocimiento(1)).thenReturn(List.of(
                new Solucion(8, 1, "Reiniciar", TipoSolucion.PASOS, 1, "Paso seguro", true, true)));
        assertEquals(List.of(new ConsultarChatbotService.SolucionChatbot("Impresora", "Paso seguro", "Reiniciar")),
                servicio.consultar(" impresora "));
        verify(busqueda).buscarAutoservicio("impresora");
        verify(soluciones).buscarActivasAutoservicioPorConocimiento(1);
        verifyNoMoreInteractions(busqueda, soluciones);
    }

    @Test void limitaCincoSolucionesYNoConsultaSiguientesConocimientos() {
        when(busqueda.buscarAutoservicio("impresora")).thenReturn(List.of(conocimiento(1), conocimiento(2)));
        when(soluciones.buscarActivasAutoservicioPorConocimiento(1)).thenReturn(IntStream.range(0, 8)
                .mapToObj(i -> new Solucion(i, 1, "Paso " + i, TipoSolucion.PASOS, i, null, true, true)).toList());
        var resultado = servicio.consultar("impresora");
        assertEquals(5, resultado.size());
        assertEquals("Paso 4", resultado.getLast().solucion());
        verify(soluciones, never()).buscarActivasAutoservicioPorConocimiento(2);
    }

    @Test void sinCoincidenciasNoLeeSoluciones() {
        when(busqueda.buscarAutoservicio("inexistente")).thenReturn(List.of());
        assertTrue(servicio.consultar("inexistente").isEmpty());
        verifyNoInteractions(soluciones);
    }
}
