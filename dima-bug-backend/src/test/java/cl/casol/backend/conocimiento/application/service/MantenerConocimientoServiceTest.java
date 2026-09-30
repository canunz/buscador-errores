package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.catalogo.application.port.out.*;
import cl.casol.backend.catalogo.domain.*;
import cl.casol.backend.conocimiento.application.port.out.ConocimientoRepository;
import cl.casol.backend.conocimiento.domain.Conocimiento;
import cl.casol.backend.conocimiento.domain.EstadoConocimiento;
import cl.casol.backend.conocimiento.domain.exception.ClasificacionInvalidaException;
import cl.casol.backend.conocimiento.domain.exception.ConocimientoNoEncontradoException;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.Rol;
import cl.casol.backend.identidad.domain.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MantenerConocimientoServiceTest {
    private ConocimientoRepository conocimientos;
    private HardwareRepository hardware;
    private SistemaRepository sistemas;
    private ModuloRepository modulos;
    private FrecuenciaRepository frecuencias;
    private UsuarioRepository usuarios;
    private IndexarConocimientoService indexador;
    private MantenerConocimientoService service;

    @BeforeEach
    void setUp() {
        conocimientos = mock(ConocimientoRepository.class); hardware = mock(HardwareRepository.class);
        sistemas = mock(SistemaRepository.class); modulos = mock(ModuloRepository.class);
        frecuencias = mock(FrecuenciaRepository.class); usuarios = mock(UsuarioRepository.class);
        indexador = mock(IndexarConocimientoService.class);
        service = new MantenerConocimientoService(conocimientos, hardware, sistemas, modulos, frecuencias, usuarios,
                indexador);
        when(conocimientos.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(usuarios.buscarPorEmail("tecnico@dimarsa.cl")).thenReturn(Optional.of(usuario()));
    }

    @Test
    void crearSiempreIniciaComoBorradorYTomaCreadorAutenticado() {
        Conocimiento creado = service.crear("Título", "Descripción", null, null, null, null, null,
                "tecnico@dimarsa.cl");
        assertEquals(EstadoConocimiento.BORRADOR, creado.getEstado());
        assertEquals(7, creado.getCreadoPor().getId());
        assertNotNull(creado.getFechaCreacion());
        assertNull(creado.getModificadoPor());
    }

    @Test
    void validaTodasLasClasificacionesActivas() {
        when(hardware.buscarActivoPorId(1)).thenReturn(Optional.of(new Hardware(1, "PC", null, true)));
        when(sistemas.buscarActivoPorId(2)).thenReturn(Optional.of(new Sistema(2, "Comercial", null, true)));
        when(modulos.buscarActivoPorId(3)).thenReturn(Optional.of(new Modulo(3, 2, "Bodega", true)));
        when(frecuencias.buscarActivaPorId(4)).thenReturn(Optional.of(new Frecuencia(4, "DIARIO", true)));

        Conocimiento creado = service.crear("Título", "Descripción", 1, 2, 3, 4, null,
                "tecnico@dimarsa.cl");
        assertEquals(3, creado.getModulo().id());
        assertEquals(4, creado.getFrecuencia().id());
    }

    @Test
    void rechazaModuloSinSistema() {
        assertThrows(ClasificacionInvalidaException.class, () -> service.crear(
                "Título", "Descripción", null, null, 3, null, null, "tecnico@dimarsa.cl"));
    }

    @Test
    void rechazaModuloDeOtroSistema() {
        when(sistemas.buscarActivoPorId(2)).thenReturn(Optional.of(new Sistema(2, "Comercial", null, true)));
        when(modulos.buscarActivoPorId(3)).thenReturn(Optional.of(new Modulo(3, 9, "Otro", true)));
        assertThrows(ClasificacionInvalidaException.class, () -> service.crear(
                "Título", "Descripción", null, 2, 3, null, null, "tecnico@dimarsa.cl"));
    }

    @Test
    void modificarConservaCreacionYRegistraModificador() {
        Conocimiento existente = conocimiento();
        when(conocimientos.buscarPorId(1)).thenReturn(Optional.of(existente));
        Conocimiento modificado = service.modificar(1, "Nuevo", "Nueva", null, null, null, null,
                "Comentario", "tecnico@dimarsa.cl");
        assertSame(existente.getCreadoPor(), modificado.getCreadoPor());
        assertEquals(7, modificado.getModificadoPor().getId());
        assertEquals("Nuevo", modificado.getTitulo());
        verify(indexador).indexar(1);
        verify(indexador, never()).indexar(argThat(id -> id != null && id != 1));
    }

    @Test
    void buscarInexistenteLanza404DeDominio() {
        when(conocimientos.buscarPorId(404)).thenReturn(Optional.empty());
        assertThrows(ConocimientoNoEncontradoException.class, () -> service.buscar(404));
    }

    @Test
    void eliminarMarcaEstadoActualizaAuditoriaYRetiraProyeccion() {
        Conocimiento existente = conocimiento();
        when(conocimientos.buscarPorIdIncluidoEliminado(1)).thenReturn(Optional.of(existente));

        service.eliminar(1, "tecnico@dimarsa.cl");

        var captor = org.mockito.ArgumentCaptor.forClass(Conocimiento.class);
        verify(conocimientos).guardar(captor.capture());
        Conocimiento eliminado = captor.getValue();
        assertEquals(EstadoConocimiento.ELIMINADO, eliminado.getEstado());
        assertEquals(7, eliminado.getModificadoPor().getId());
        assertNotNull(eliminado.getFechaModificacion());
        assertSame(existente.getCreadoPor(), eliminado.getCreadoPor());
        verify(indexador).eliminar(1);
    }

    @Test
    void eliminarEsIdempotenteSiYaEstaEliminado() {
        Conocimiento eliminado = new Conocimiento(1, "TÃ­tulo", "DescripciÃ³n", EstadoConocimiento.ELIMINADO,
                null, null, null, null, null, usuario(), java.time.LocalDateTime.now(), usuario(),
                java.time.LocalDateTime.now());
        when(conocimientos.buscarPorIdIncluidoEliminado(1)).thenReturn(Optional.of(eliminado));

        service.eliminar(1, "tecnico@dimarsa.cl");

        verify(conocimientos, never()).guardar(any());
        verifyNoInteractions(indexador);
        verify(usuarios, never()).buscarPorEmail(anyString());
    }

    @Test
    void eliminarInexistenteLanza404() {
        when(conocimientos.buscarPorIdIncluidoEliminado(404)).thenReturn(Optional.empty());
        assertThrows(ConocimientoNoEncontradoException.class,
                () -> service.eliminar(404, "tecnico@dimarsa.cl"));
        verify(conocimientos, never()).guardar(any());
    }

    @Test
    void eliminadoNoPuedeModificarseNiRepublicarse() {
        when(conocimientos.buscarPorId(1)).thenReturn(Optional.empty());
        assertThrows(ConocimientoNoEncontradoException.class, () -> service.modificar(1, "Nuevo", "Nueva",
                null, null, null, null, null, "tecnico@dimarsa.cl"));
        assertThrows(ConocimientoNoEncontradoException.class,
                () -> service.cambiarEstado(1, EstadoConocimiento.PUBLICADO, "tecnico@dimarsa.cl"));
        verify(conocimientos, never()).guardar(any());
    }

    private Usuario usuario() {
        return new Usuario(7, new Rol(2, "TECNICO", null, true, null, null), "Carolina",
                "tecnico@dimarsa.cl", "hash", true, null, null);
    }

    private Conocimiento conocimiento() {
        return new Conocimiento(1, "Título", "Descripción", EstadoConocimiento.BORRADOR,
                null, null, null, null, null, usuario(), java.time.LocalDateTime.now(), null, null);
    }
}
