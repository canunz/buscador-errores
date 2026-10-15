package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.*;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.parser.PartTree;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class PruebasPersistenciaQa013Test {
 @Test void guardarPreservaIdEstadoResultadoSinEliminar(){var repo=mock(PruebaJpaRepository.class);when(repo.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));var adapter=new PruebaAdministrativaRepositoryAdapter(repo,mock(EntityManager.class));var p=new Prueba(3,"Ping","Responde",false);assertEquals(p,adapter.guardar(p));verify(repo,never()).deleteById(anyInt());}
 @Test void altaMantieneIdGeneradoJpa(){var repo=mock(PruebaJpaRepository.class);when(repo.saveAndFlush(any())).thenReturn(new PruebaEntity(9,"Ping",null,true));var adapter=new PruebaAdministrativaRepositoryAdapter(repo,mock(EntityManager.class));assertEquals(9,adapter.guardar(new Prueba(null,"Ping",null,true)).id());}
 @Test void listaInactivasConOrdenEstable(){var repo=mock(PruebaJpaRepository.class);when(repo.findAll()).thenReturn(List.of(new PruebaEntity(9,"Z",null,true),new PruebaEntity(2,"A","OK",false)));var r=new PruebaAdministrativaRepositoryAdapter(repo,mock(EntityManager.class)).listar();assertEquals(List.of(2,9),r.stream().map(Prueba::id).toList());assertFalse(r.getFirst().activa());}
 @Test void bloqueoRefrescaEstado(){var repo=mock(PruebaJpaRepository.class);var em=mock(EntityManager.class);var p=new PruebaEntity(3,"Ping",null,false);when(em.find(PruebaEntity.class,3,LockModeType.PESSIMISTIC_WRITE)).thenReturn(p);new PruebaAdministrativaRepositoryAdapter(repo,em).bloquear(3);verify(em).refresh(p,LockModeType.PESSIMISTIC_WRITE);}
 @Test void consultaHistoricaNoFiltraEstado(){var repo=mock(ConocimientoPruebaJpaRepository.class);var p=new PruebaEntity(3,"Ping","Responde",false);when(repo.findByIdConocimientoIdOrderByOrdenAscIdPruebaIdAsc(4)).thenReturn(List.of(new ConocimientoPruebaEntity(new ConocimientoPruebaId(4,3),p,2)));var r=new ConocimientoPruebaRepositoryAdapter(repo,mock(PruebaJpaRepository.class)).buscarPorConocimientoOrdenadas(4);assertFalse(r.getFirst().prueba().activa());assertEquals(3,r.getFirst().prueba().id());assertEquals(2,r.getFirst().orden());}
 @Test void consultaDerivadaReconoceOrdenYDesempate(){assertDoesNotThrow(()->new PartTree("findByIdConocimientoIdOrderByOrdenAscIdPruebaIdAsc",ConocimientoPruebaEntity.class));}
}