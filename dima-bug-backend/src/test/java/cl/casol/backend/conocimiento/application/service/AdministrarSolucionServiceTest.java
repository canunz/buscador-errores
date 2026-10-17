package cl.casol.backend.conocimiento.application.service;
import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdministrarSolucionServiceTest {
    ConocimientoRepository conocimientos;
    SolucionRepository soluciones;
    IndexarConocimientoService indexador;
    AdministrarSolucionService service;
    @BeforeEach void setup() {
        conocimientos=mock(ConocimientoRepository.class); soluciones=mock(SolucionRepository.class);
        indexador=mock(IndexarConocimientoService.class);
        service=new AdministrarSolucionService(conocimientos,soluciones,indexador);
        when(soluciones.guardar(any())).thenAnswer(i -> i.getArgument(0));
    }
    void caso(EstadoConocimiento estado, Solucion... lista) {
        Conocimiento c=mock(Conocimiento.class); when(c.getEstado()).thenReturn(estado);
        when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(c));
        when(soluciones.bloquearPorConocimiento(4)).thenReturn(List.of(lista));
    }
    Solucion s(int id,boolean activo,boolean auto) { return new Solucion(id,4,"Instrucciones",TipoSolucion.PASOS,1,null,activo,auto); }
    @Test void publicadoConUltimaActivaRechaza() {
        caso(EstadoConocimiento.PUBLICADO,s(8,true,false),s(9,false,false));
        assertThrows(UltimaSolucionActivaException.class,()->service.cambiarEstado(4,8,false));
        verify(soluciones,never()).guardar(any()); verifyNoInteractions(indexador);
    }
    @Test void publicadoConDosActivasDesactivaSinPerderDatos() {
        caso(EstadoConocimiento.PUBLICADO,s(8,true,true),s(9,true,false));
        Solucion r=service.cambiarEstado(4,8,false);
        assertFalse(r.activo()); assertTrue(r.autoservicio()); assertNull(r.titulo());
        assertEquals("Instrucciones",r.descripcion()); assertEquals(8,r.id());
        verify(indexador).indexar(4);
    }
    @Test void borradorPermiteDesactivarUltima() {
        caso(EstadoConocimiento.BORRADOR,s(8,true,false));
        assertFalse(service.cambiarEstado(4,8,false).activo());
    }
    @Test void reactivaConservandoAutoservicio() {
        caso(EstadoConocimiento.BORRADOR,s(8,false,true));
        Solucion r=service.cambiarEstado(4,8,true); assertTrue(r.activo()); assertTrue(r.autoservicio());
    }
    @Test void estadoEsIdempotente() {
        caso(EstadoConocimiento.PUBLICADO,s(8,true,false));
        assertTrue(service.cambiarEstado(4,8,true).activo()); verify(soluciones,never()).guardar(any());
    }
    @Test void autoservicioPreservaEstadoYContenido() {
        caso(EstadoConocimiento.BORRADOR,s(8,false,false));
        Solucion r=service.cambiarAutoservicio(4,8,true); assertFalse(r.activo()); assertTrue(r.autoservicio());
        assertEquals("Instrucciones",r.descripcion());
    }
    @Test void solucionAjenaNoModifica() {
        caso(EstadoConocimiento.BORRADOR,s(9,true,false));
        assertThrows(SolucionNoEncontradaException.class,()->service.cambiarEstado(4,8,false));
        verify(soluciones,never()).guardar(any());
    }
    @Test void padreInexistenteNoModifica() {
        assertThrows(ConocimientoNoEncontradoException.class,()->service.cambiarEstado(4,8,false));
        verifyNoInteractions(soluciones);
    }
    @Test void adminListaTambienInactivas() {
        caso(EstadoConocimiento.BORRADOR);
        when(soluciones.buscarPorConocimientoOrdenadas(4)).thenReturn(List.of(s(8,false,false)));
        assertFalse(service.listar(4).getFirst().activo());
    }
    @Test void bloqueoAntesDeLeerYContar() {
        caso(EstadoConocimiento.BORRADOR,s(8,true,false)); service.cambiarEstado(4,8,false);
        var orden=inOrder(conocimientos,soluciones);
        orden.verify(conocimientos).bloquear(4); orden.verify(conocimientos).buscarPorId(4);
        orden.verify(soluciones).bloquearPorConocimiento(4); orden.verify(soluciones).guardar(any());
    }
}
