package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.BusquedaConocimientoRepository;
import cl.casol.backend.conocimiento.domain.ResultadoBusquedaConocimiento;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class BuscarConocimientoServiceTest {
    @Test
    void normalizaTextoYCombinaTodosLosFiltros() {
        BusquedaConocimientoRepository repository = mock(BusquedaConocimientoRepository.class);
        ResultadoBusquedaConocimiento resultado = new ResultadoBusquedaConocimiento(
                10, "Firewall", 1, "POS", 6, "Ventas", 2, "Caja", 3, "Alta", 1.5);
        when(repository.buscar("no conecta", 1, 6, 2, 3)).thenReturn(List.of(resultado));

        List<ResultadoBusquedaConocimiento> encontrados =
                new BuscarConocimientoService(repository).buscar("  no conecta  ", 1, 6, 2, 3);

        assertEquals(List.of(resultado), encontrados);
    }

    @Test
    void textoNuloVacioOEspaciosSeBuscaComoAusente() {
        BusquedaConocimientoRepository repository = mock(BusquedaConocimientoRepository.class);
        BuscarConocimientoService service = new BuscarConocimientoService(repository);
        when(repository.buscar(null, null, null, null, null)).thenReturn(List.of());

        assertTrue(service.buscar(null, null, null, null, null).isEmpty());
        assertTrue(service.buscar("", null, null, null, null).isEmpty());
        assertTrue(service.buscar("   ", null, null, null, null).isEmpty());
        verify(repository, times(3)).buscar(null, null, null, null, null);
    }
}
