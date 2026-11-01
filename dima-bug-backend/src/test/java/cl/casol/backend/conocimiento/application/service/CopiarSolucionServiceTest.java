package cl.casol.backend.conocimiento.application.service;
import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class CopiarSolucionServiceTest {
    final ConocimientoRepository conocimientos=mock(ConocimientoRepository.class);
    final SolucionRepository soluciones=mock(SolucionRepository.class);
    final IndexarConocimientoService indexador=mock(IndexarConocimientoService.class);
    final CopiarSolucionService service=new CopiarSolucionService(conocimientos,soluciones,indexador);
    final Solucion original=new Solucion(8,1,"  Paso A\nPaso B  ",TipoSolucion.DERIVACION,3," Título ",true,true);
    Conocimiento conocimiento(int id,EstadoConocimiento estado){return new Conocimiento(id,"K"+id,"D",estado,null,null,null,null,null,null,null,null,null);}
    @BeforeEach void preparar(){
        when(conocimientos.buscarPorId(1)).thenReturn(Optional.of(conocimiento(1,EstadoConocimiento.PUBLICADO)));
        when(conocimientos.buscarPorId(2)).thenReturn(Optional.of(conocimiento(2,EstadoConocimiento.BORRADOR)));
        when(soluciones.bloquearPorConocimiento(1)).thenReturn(List.of(original));
        when(soluciones.guardar(any())).thenAnswer(i->{Solucion s=i.getArgument(0);return new Solucion(99,s.conocimientoId(),s.descripcion(),s.tipo(),s.orden(),s.titulo(),s.activo(),s.autoservicio());});
    }
    @Test void copiaIndependienteConIdNuevoYContenidoLiteral(){var copia=service.copiar(1,8,2,false);assertEquals(99,copia.id());assertNotEquals(original.id(),copia.id());assertEquals(2,copia.conocimientoId());assertEquals(original.titulo(),copia.titulo());assertEquals(original.descripcion(),copia.descripcion());assertEquals(original.tipo(),copia.tipo());assertTrue(copia.activo());assertFalse(copia.autoservicio());verify(indexador).indexar(2);}
    @Test void ordenIncluyeInactivas(){when(soluciones.bloquearPorConocimiento(2)).thenReturn(List.of(new Solucion(4,2,"Otra",TipoSolucion.PASOS,9,"Otra",false,false)));assertEquals(10,service.copiar(1,8,2,false).orden());}
    @Test void conservaEstadoInactivoSinCopiarAutoservicio(){when(soluciones.bloquearPorConocimiento(1)).thenReturn(List.of(new Solucion(8,1,"D",TipoSolucion.PASOS,3,null,false,true)));var copia=service.copiar(1,8,2,false);assertFalse(copia.activo());assertNull(copia.titulo());assertFalse(copia.autoservicio());}
    @Test void habilitaAutoservicioSoloPorConfirmacion(){assertTrue(service.copiar(1,8,2,true).autoservicio());}
    @Test void permiteConfirmarAutoservicioDeOrigenPrivado(){when(soluciones.bloquearPorConocimiento(1)).thenReturn(List.of(new Solucion(8,1,"D",TipoSolucion.PASOS,1,"T",true,false)));assertTrue(service.copiar(1,8,2,true).autoservicio());}
    @Test void rechazaOrigenBorrador(){when(conocimientos.buscarPorId(1)).thenReturn(Optional.of(conocimiento(1,EstadoConocimiento.BORRADOR)));assertThrows(CopiaSolucionInvalidaException.class,()->service.copiar(1,8,2,false));verify(soluciones,never()).guardar(any());}
    @Test void destinoInexistente(){when(conocimientos.buscarPorId(2)).thenReturn(Optional.empty());assertThrows(ConocimientoNoEncontradoException.class,()->service.copiar(1,8,2,false));}
    @Test void origenInexistente(){when(conocimientos.buscarPorId(1)).thenReturn(Optional.empty());assertThrows(ConocimientoNoEncontradoException.class,()->service.copiar(1,8,2,false));}
    @Test void solucionAjenaNoSeCopia(){assertThrows(SolucionNoEncontradaException.class,()->service.copiar(1,99,2,false));verify(soluciones,never()).guardar(any());}
    @Test void mismoConocimientoNoSeCopia(){assertThrows(CopiaSolucionInvalidaException.class,()->service.copiar(1,8,1,false));verifyNoInteractions(conocimientos,soluciones,indexador);}
    @Test void duplicadoNormalizaMayusculasEspaciosYUnicode(){when(soluciones.bloquearPorConocimiento(2)).thenReturn(List.of(new Solucion(4,2,"PASO A Paso B",TipoSolucion.DERIVACION,2,"ti\u0301tulo",false,false)));assertThrows(SolucionDuplicadaException.class,()->service.copiar(1,8,2,false));verify(soluciones,never()).guardar(any());}
    @Test void mismoTituloDistintoContenidoNoEsDuplicado(){when(soluciones.bloquearPorConocimiento(2)).thenReturn(List.of(new Solucion(4,2,"Otra descripción",TipoSolucion.DERIVACION,2,"Título",true,false)));assertEquals(3,service.copiar(1,8,2,false).orden());}
    @Test void bloqueoDePadresOrdenadoYAnteriorALectura(){service.copiar(1,8,2,false);var orden=inOrder(conocimientos,soluciones);orden.verify(conocimientos).bloquear(1);orden.verify(conocimientos).bloquear(2);orden.verify(conocimientos).buscarPorId(1);orden.verify(conocimientos).buscarPorId(2);orden.verify(soluciones).bloquearPorConocimiento(1);orden.verify(soluciones).bloquearPorConocimiento(2);orden.verify(soluciones).guardar(any());}
    @Test void ordenAgotadoNoInserta(){when(soluciones.bloquearPorConocimiento(2)).thenReturn(List.of(new Solucion(4,2,"D",TipoSolucion.PASOS,Integer.MAX_VALUE,"Otro",true,false)));assertThrows(CopiaSolucionInvalidaException.class,()->service.copiar(1,8,2,false));}
    @Test void publicadoNoPuedeQuedarSinSolucionesActivas(){when(conocimientos.buscarPorId(2)).thenReturn(Optional.of(conocimiento(2,EstadoConocimiento.PUBLICADO)));when(soluciones.bloquearPorConocimiento(1)).thenReturn(List.of(new Solucion(8,1,"D",TipoSolucion.PASOS,1,"T",false,false)));assertThrows(UltimaSolucionActivaException.class,()->service.copiar(1,8,2,false));}
    @Test void falloIndiceSePropagaParaRollback(){doThrow(new IllegalStateException("índice")).when(indexador).indexar(2);assertThrows(IllegalStateException.class,()->service.copiar(1,8,2,false));}
}
