package cl.casol.backend.conocimiento.application.service;
import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AdministrarPruebasServiceTest {
 final PruebaAdministrativaRepository repo=mock(PruebaAdministrativaRepository.class);
 final ApplicationEventPublisher eventos=mock(ApplicationEventPublisher.class);
 final AdministrarPruebasService service=new AdministrarPruebasService(repo,eventos);
 final Prueba p=new Prueba(1,"Ping","Responde",true);
 void guardar(){when(repo.guardar(any())).thenAnswer(i->i.getArgument(0));}
 @Test void consultaActivasEInactivas(){when(repo.listar()).thenReturn(List.of(p,new Prueba(2,"Cable",null,false)));assertEquals(2,service.listar().size());assertFalse(service.listar().get(1).activa());}
 @Test void creaActivaNormalizada(){guardar();var r=service.crear(" Ping "," OK ");assertTrue(r.activa());assertEquals("Ping",r.descripcion());assertEquals("OK",r.resultadoEsperado());}
 @Test void resultadoOpcionalCompatible(){guardar();assertNull(service.crear("Ping"," ").resultadoEsperado());}
 @Test void validaDescripcion(){for(var d:Arrays.asList(null," ","x".repeat(501)))assertThrows(PruebaInvalidaException.class,()->service.crear(d,null));verify(repo,never()).guardar(any());}
 @Test void validaResultado(){assertThrows(PruebaInvalidaException.class,()->service.crear("Ping","x".repeat(501)));}
 @Test void duplicadoIncluyeInactivas(){when(repo.listar()).thenReturn(List.of(new Prueba(2,"Ping",null,false)));assertThrows(PruebaConflictoException.class,()->service.crear(" Ping ",null));}
 @Test void editarPreservaEstadoAdministrativo(){guardar();when(repo.buscar(1)).thenReturn(Optional.of(new Prueba(1,"Ping",null,false)));assertFalse(service.editar(1,"Cable","OK").activa());verify(repo).bloquear(1);verify(eventos).publishEvent(new PruebaActualizada(1));}
 @Test void editarExcluyeSuPropioDuplicado(){guardar();when(repo.buscar(1)).thenReturn(Optional.of(p));when(repo.listar()).thenReturn(List.of(p));assertEquals("Ping",service.editar(1,"Ping","Nuevo").descripcion());}
 @Test void desactivaSinModificarRelaciones(){guardar();when(repo.buscar(1)).thenReturn(Optional.of(p));var r=service.estado(1,false);assertFalse(r.activa());assertEquals(p.resultadoEsperado(),r.resultadoEsperado());verify(repo).guardar(r);verify(repo,never()).conocimientos(anyInt());}
 @Test void reactivaSinCambiarDefinicion(){guardar();when(repo.buscar(1)).thenReturn(Optional.of(new Prueba(1,"Ping","Responde",false)));assertEquals(p,service.estado(1,true));}
 @Test void estadoIdempotenteNoReescribeYReintentaIndice(){when(repo.buscar(1)).thenReturn(Optional.of(p));assertEquals(p,service.estado(1,true));verify(repo,never()).guardar(any());verify(eventos).publishEvent(new PruebaActualizada(1));}
 @Test void inexistente404(){assertThrows(PruebaNoEncontradaException.class,()->service.estado(99,false));}
 @Test void reindexaTodosLosUsosNoEliminados(){var c=mock(ConocimientoRepository.class);var index=mock(IndexarConocimientoService.class);when(repo.conocimientos(1)).thenReturn(List.of(2,3));when(c.buscarPorId(2)).thenReturn(Optional.of(mock(Conocimiento.class)));new ReindexarPruebaService(repo,c,index).actualizar(1);verify(index).indexar(2);verify(index,never()).indexar(3);}
 @Test void falloIndicePostCommitNoRevierteGuardado(){var worker=mock(ReindexarPruebaService.class);doThrow(new IllegalStateException("simulado")).when(worker).actualizar(1);assertDoesNotThrow(()->new ReindexarPruebaListener(worker).actualizar(new PruebaActualizada(1)));}
}