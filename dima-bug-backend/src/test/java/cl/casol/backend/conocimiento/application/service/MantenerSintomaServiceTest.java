package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.ConocimientoRepository;
import cl.casol.backend.conocimiento.application.port.out.SintomaRepository;
import cl.casol.backend.conocimiento.domain.Conocimiento;
import cl.casol.backend.conocimiento.domain.Sintoma;
import cl.casol.backend.conocimiento.domain.exception.ConocimientoNoEncontradoException;
import cl.casol.backend.conocimiento.domain.exception.SintomaNoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MantenerSintomaServiceTest {
    private ConocimientoRepository conocimientos;
    private SintomaRepository sintomas;
    private IndexarConocimientoService indexador;
    private MantenerSintomaService service;
    private Conocimiento conocimiento;

    @BeforeEach void setUp() {
        conocimientos = mock(ConocimientoRepository.class);
        sintomas = mock(SintomaRepository.class);
        indexador = mock(IndexarConocimientoService.class);
        service = new MantenerSintomaService(conocimientos, sintomas, indexador);
        conocimiento = mock(Conocimiento.class);
    }

    @Test void listaSintomasDeConocimientoExistenteEnOrdenDelRepositorio() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(sintomas.buscarPorConocimientoOrdenados(4)).thenReturn(List.of(
                new Sintoma(2, 4, "Primero", 1), new Sintoma(1, 4, "Segundo", 2)));

        List<Sintoma> resultado = service.listar(4);

        assertEquals(List.of(1, 2), resultado.stream().map(Sintoma::orden).toList());
        verify(sintomas).buscarPorConocimientoOrdenados(4);
    }

    @Test void listaVaciaCuandoConocimientoExisteSinSintomas() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(sintomas.buscarPorConocimientoOrdenados(4)).thenReturn(List.of());
        assertTrue(service.listar(4).isEmpty());
    }

    @Test void listarConConocimientoInexistenteDevuelve404DeDominio() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.empty());
        assertThrows(ConocimientoNoEncontradoException.class, () -> service.listar(4));
        verifyNoInteractions(sintomas);
    }

    @Test void creaSintomaVinculadoYUsaOrdenUnoPorDefecto() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(sintomas.guardar(any())).thenAnswer(invocation -> {
            Sintoma recibido = invocation.getArgument(0);
            return new Sintoma(9, recibido.conocimientoId(), recibido.descripcion(), recibido.orden());
        });

        Sintoma creado = service.crear(4, "Mensaje", null);

        assertEquals(9, creado.id());
        assertEquals(4, creado.conocimientoId());
        assertEquals(1, creado.orden());
        verify(indexador).indexar(4);
    }

    @Test void crearConConocimientoInexistenteNoPersiste() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.empty());
        assertThrows(ConocimientoNoEncontradoException.class, () -> service.crear(4, "Mensaje", 1));
        verifyNoInteractions(sintomas);
    }

    @Test void modificaSintomaDelConocimiento() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(sintomas.buscarPorId(9)).thenReturn(Optional.of(new Sintoma(9, 4, "Anterior", 1)));
        when(sintomas.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Sintoma resultado = service.modificar(4, 9, "Nuevo", 2);

        assertEquals(new Sintoma(9, 4, "Nuevo", 2), resultado);
        verify(indexador).indexar(4);
    }

    @Test void modificarSintomaInexistenteNoPersiste() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(sintomas.buscarPorId(9)).thenReturn(Optional.empty());
        assertThrows(SintomaNoEncontradoException.class, () -> service.modificar(4, 9, "Nuevo", 2));
        verify(sintomas, never()).guardar(any());
    }

    @Test void modificarSintomaDeOtroConocimientoDevuelve404YNoPersiste() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(conocimiento));
        when(sintomas.buscarPorId(9)).thenReturn(Optional.of(new Sintoma(9, 8, "Ajeno", 1)));
        assertThrows(SintomaNoEncontradoException.class, () -> service.modificar(4, 9, "Nuevo", 2));
        verify(sintomas, never()).guardar(any());
    }

    @Test void modificarVerificaPrimeroElConocimiento() {
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.empty());
        assertThrows(ConocimientoNoEncontradoException.class, () -> service.modificar(4, 9, "Nuevo", 2));
        verifyNoInteractions(sintomas);
    }
}
