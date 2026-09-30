package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.CausaRepository;
import cl.casol.backend.conocimiento.application.port.out.ConocimientoRepository;
import cl.casol.backend.conocimiento.domain.Causa;
import cl.casol.backend.conocimiento.domain.Conocimiento;
import cl.casol.backend.conocimiento.domain.exception.CausaNoEncontradaException;
import cl.casol.backend.conocimiento.domain.exception.ConocimientoNoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MantenerCausaServiceTest {
    private ConocimientoRepository conocimientos;
    private CausaRepository causas;
    private IndexarConocimientoService indexador;
    private MantenerCausaService service;
    private Conocimiento conocimiento;

    @BeforeEach void setUp() {
        conocimientos = mock(ConocimientoRepository.class);
        causas = mock(CausaRepository.class);
        indexador = mock(IndexarConocimientoService.class);
        service = new MantenerCausaService(conocimientos, causas, indexador);
        conocimiento = mock(Conocimiento.class);
    }

    @Test void listaCausasDeConocimientoExistenteEnOrdenDelRepositorio() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(causas.buscarPorConocimientoOrdenadas(4)).thenReturn(List.of(
                new Causa(2, 4, "Primera", 1), new Causa(1, 4, "Segunda", 2)));

        List<Causa> resultado = service.listar(4);

        assertEquals(List.of(1, 2), resultado.stream().map(Causa::orden).toList());
        verify(causas).buscarPorConocimientoOrdenadas(4);
    }

    @Test void listaVaciaCuandoConocimientoExisteSinCausas() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(causas.buscarPorConocimientoOrdenadas(4)).thenReturn(List.of());
        assertTrue(service.listar(4).isEmpty());
    }

    @Test void listarConConocimientoInexistenteDevuelve404DeDominio() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.empty());
        assertThrows(ConocimientoNoEncontradoException.class, () -> service.listar(4));
        verifyNoInteractions(causas);
    }

    @Test void creaCausaVinculadaConOrdenInformado() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(causas.guardar(any())).thenAnswer(invocation -> {
            Causa recibida = invocation.getArgument(0);
            return new Causa(9, recibida.conocimientoId(), recibida.descripcion(), recibida.orden());
        });
        Causa creada = service.crear(4, "Cola bloqueada", 3);
        assertEquals(new Causa(9, 4, "Cola bloqueada", 3), creada);
        verify(indexador).indexar(4);
    }

    @Test void creaCausaConOrdenUnoCuandoSeOmite() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(causas.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(1, service.crear(4, "Cola bloqueada", null).orden());
    }

    @Test void crearConConocimientoInexistenteNoPersiste() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.empty());
        assertThrows(ConocimientoNoEncontradoException.class,
                () -> service.crear(4, "Cola bloqueada", 1));
        verifyNoInteractions(causas);
    }

    @Test void modificaCausaDelConocimiento() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(causas.buscarPorId(9)).thenReturn(Optional.of(new Causa(9, 4, "Anterior", 1)));
        when(causas.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(new Causa(9, 4, "Nueva", 2), service.modificar(4, 9, "Nueva", 2));
        verify(indexador).indexar(4);
    }

    @Test void modificarCausaInexistenteNoPersiste() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(causas.buscarPorId(9)).thenReturn(Optional.empty());
        assertThrows(CausaNoEncontradaException.class, () -> service.modificar(4, 9, "Nueva", 2));
        verify(causas, never()).guardar(any());
    }

    @Test void modificarCausaDeOtroConocimientoDevuelve404YNoPersiste() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(causas.buscarPorId(9)).thenReturn(Optional.of(new Causa(9, 8, "Ajena", 1)));
        assertThrows(CausaNoEncontradaException.class, () -> service.modificar(4, 9, "Nueva", 2));
        verify(causas, never()).guardar(any());
    }

    @Test void modificarVerificaPrimeroElConocimiento() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.empty());
        assertThrows(ConocimientoNoEncontradoException.class, () -> service.modificar(4, 9, "Nueva", 2));
        verifyNoInteractions(causas);
    }
}
