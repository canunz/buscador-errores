package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MantenerSolucionServiceTest {
    ConocimientoRepository conocimientos; SolucionRepository soluciones; IndexarConocimientoService indexador; MantenerSolucionService service;
    @BeforeEach void setUp() { conocimientos=mock(ConocimientoRepository.class); soluciones=mock(SolucionRepository.class);
        indexador=mock(IndexarConocimientoService.class); service=new MantenerSolucionService(conocimientos, soluciones, indexador); }
    void existe() { when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(mock(Conocimiento.class))); }
    @Test void listaOrdenada() { existe(); when(soluciones.buscarPorConocimientoOrdenadas(4)).thenReturn(List.of(
            new Solucion(1,4,"A",TipoSolucion.PASOS,1),new Solucion(2,4,"B",TipoSolucion.DERIVACION,2)));
        assertEquals(List.of(1,2),service.listar(4).stream().map(Solucion::orden).toList()); }
    @Test void listaVacia() { existe(); when(soluciones.buscarPorConocimientoOrdenadas(4)).thenReturn(List.of());
        assertTrue(service.listar(4).isEmpty()); }
    @Test void listarPadreInexistente() { when(conocimientos.buscarPorId(4)).thenReturn(Optional.empty());
        assertThrows(ConocimientoNoEncontradoException.class,()->service.listar(4)); verifyNoInteractions(soluciones); }
    @Test void creaPasos() { existe(); when(soluciones.guardar(any())).thenAnswer(i->i.getArgument(0));
        assertEquals(TipoSolucion.PASOS,service.crear(4,"Pasos",TipoSolucion.PASOS,2).tipo()); verify(indexador).indexar(4); }
    @Test void creaDerivacion() { existe(); when(soluciones.guardar(any())).thenAnswer(i->i.getArgument(0));
        assertEquals(TipoSolucion.DERIVACION,service.crear(4,"Derivar",TipoSolucion.DERIVACION,1).tipo()); }
    @Test void aplicaValoresPredeterminados() { existe(); when(soluciones.guardar(any())).thenAnswer(i->i.getArgument(0));
        Solucion s=service.crear(4,"Pasos",null,null); assertEquals(TipoSolucion.PASOS,s.tipo()); assertEquals(1,s.orden()); }
    @Test void modificaPerteneciente() { existe(); when(soluciones.buscarPorId(8)).thenReturn(Optional.of(
            new Solucion(8,4,"Vieja",TipoSolucion.PASOS,1))); when(soluciones.guardar(any())).thenAnswer(i->i.getArgument(0));
        assertEquals(new Solucion(8,4,"Nueva",TipoSolucion.DERIVACION,2),service.modificar(4,8,"Nueva",TipoSolucion.DERIVACION,2)); verify(indexador).indexar(4); }
    @Test void modificarInexistente() { existe(); when(soluciones.buscarPorId(8)).thenReturn(Optional.empty());
        assertThrows(SolucionNoEncontradaException.class,()->service.modificar(4,8,"X",TipoSolucion.PASOS,1)); }
    @Test void modificarDeOtroConocimiento() { existe(); when(soluciones.buscarPorId(8)).thenReturn(Optional.of(
            new Solucion(8,9,"Ajena",TipoSolucion.PASOS,1)));
        assertThrows(SolucionNoEncontradaException.class,()->service.modificar(4,8,"X",TipoSolucion.PASOS,1));
        verify(soluciones,never()).guardar(any()); }
}
