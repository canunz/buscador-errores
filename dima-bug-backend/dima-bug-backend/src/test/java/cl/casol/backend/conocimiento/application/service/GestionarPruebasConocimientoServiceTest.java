package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GestionarPruebasConocimientoServiceTest {
    private ConocimientoRepository conocimientos;
    private PruebaRepository pruebas;
    private ConocimientoPruebaRepository asociaciones;
    private GestionarPruebasConocimientoService service;
    private Conocimiento conocimiento;
    private Prueba prueba;

    @BeforeEach void setUp() {
        conocimientos = mock(ConocimientoRepository.class);
        pruebas = mock(PruebaRepository.class);
        asociaciones = mock(ConocimientoPruebaRepository.class);
        service = new GestionarPruebasConocimientoService(conocimientos, pruebas, asociaciones);
        conocimiento = mock(Conocimiento.class);
        prueba = new Prueba(3, "Hacer ping", "Responde", true);
    }

    @Test void catalogoDevuelvePruebasActivasEnOrdenDelRepositorio() {
        when(pruebas.buscarActivasOrdenadasPorDescripcion()).thenReturn(List.of(
                new Prueba(2, "Comprobar cable", null, true), prueba));
        assertEquals(List.of("Comprobar cable", "Hacer ping"),
                service.listarCatalogo().stream().map(Prueba::descripcion).toList());
        verify(pruebas).buscarActivasOrdenadasPorDescripcion();
    }

    @Test void listaAsociacionesActivasOrdenadasCuandoConocimientoExiste() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(asociaciones.buscarActivasPorConocimientoOrdenadas(4)).thenReturn(List.of(
                new ConocimientoPrueba(4, prueba, 1),
                new ConocimientoPrueba(4, new Prueba(5, "Traceroute", null, true), 2)));
        assertEquals(List.of(1, 2), service.listarPorConocimiento(4).stream()
                .map(ConocimientoPrueba::orden).toList());
    }

    @Test void conocimientoSinPruebasDevuelveListaVacia() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(asociaciones.buscarActivasPorConocimientoOrdenadas(4)).thenReturn(List.of());
        assertTrue(service.listarPorConocimiento(4).isEmpty());
    }

    @Test void listarConocimientoInexistenteNoConsultaAsociaciones() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.empty());
        assertThrows(ConocimientoNoEncontradoException.class, () -> service.listarPorConocimiento(4));
        verifyNoInteractions(asociaciones);
    }

    @Test void asociacionValidaSePersisteConVinculoYOrden() {
        prepararConocimientoYPrueba();
        when(asociaciones.existe(4, 3)).thenReturn(false);
        when(asociaciones.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(new ConocimientoPrueba(4, prueba, 2), service.asociar(4, 3, 2));
    }

    @Test void asociacionUsaOrdenUnoCuandoSeOmite() {
        prepararConocimientoYPrueba();
        when(asociaciones.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(1, service.asociar(4, 3, null).orden());
    }

    @Test void asociarConConocimientoInexistenteFallaAntesDeConsultarPrueba() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.empty());
        assertThrows(ConocimientoNoEncontradoException.class, () -> service.asociar(4, 3, 1));
        verifyNoInteractions(pruebas, asociaciones);
    }

    @Test void pruebaInexistenteOInactivaDevuelve404() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(pruebas.buscarActivaPorId(3)).thenReturn(Optional.empty());
        assertThrows(PruebaNoEncontradaException.class, () -> service.asociar(4, 3, 1));
        verifyNoInteractions(asociaciones);
    }

    @Test void asociacionDuplicadaDevuelveConflictoYNoGuarda() {
        prepararConocimientoYPrueba();
        when(asociaciones.existe(4, 3)).thenReturn(true);
        assertThrows(PruebaYaAsociadaException.class, () -> service.asociar(4, 3, 1));
        verify(asociaciones, never()).guardar(any());
    }

    @Test void actualizarOrdenConservaDefinicionGlobalDePrueba() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(asociaciones.buscarPorIds(4, 3)).thenReturn(Optional.of(new ConocimientoPrueba(4, prueba, 1)));
        when(asociaciones.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ConocimientoPrueba resultado = service.actualizarOrden(4, 3, 5);
        assertEquals(5, resultado.orden());
        assertSame(prueba, resultado.prueba());
        verifyNoInteractions(pruebas);
    }

    @Test void actualizarConConocimientoInexistenteNoConsultaAsociacion() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.empty());
        assertThrows(ConocimientoNoEncontradoException.class, () -> service.actualizarOrden(4, 3, 2));
        verifyNoInteractions(asociaciones);
    }

    @Test void actualizarAsociacionInexistenteDevuelve404() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(asociaciones.buscarPorIds(4, 3)).thenReturn(Optional.empty());
        assertThrows(AsociacionPruebaNoEncontradaException.class, () -> service.actualizarOrden(4, 3, 2));
        verify(asociaciones, never()).guardar(any());
    }

    private void prepararConocimientoYPrueba() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(pruebas.buscarActivaPorId(3)).thenReturn(Optional.of(prueba));
    }
}
