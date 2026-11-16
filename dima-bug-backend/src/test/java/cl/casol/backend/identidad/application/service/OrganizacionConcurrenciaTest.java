package cl.casol.backend.identidad.application.service;
import cl.casol.backend.identidad.application.port.out.AdministracionOrganizacionRepository;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.domain.exception.OrganizacionConflictoException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
// Simula la exclusión del puerto; no sustituye una prueba transaccional real de InnoDB.
class OrganizacionConcurrenciaTest {
  @Test void creacionConcurrenteEnCatalogoVacioTieneUnSoloGanador() throws Exception {
    var repo=mock(AdministracionOrganizacionRepository.class); var lock=new ReentrantLock();
    var datos=new ArrayList<DepartamentoAdministrativo>();
    doAnswer(i->{lock.lock();return null;}).when(repo).bloquearEscrituras();
    when(repo.listarDepartamentos()).thenAnswer(i->List.copyOf(datos));
    when(repo.guardarDepartamento(any())).thenAnswer(i->{var d=i.getArgument(0,DepartamentoAdministrativo.class);var nuevo=new DepartamentoAdministrativo(1,d.nombre(),true,0L);datos.add(nuevo);return nuevo;});
    var service=new AdministrarOrganizacionService(repo,mock(ApplicationEventPublisher.class));
    var inicio=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(2);
    Callable<Boolean> tarea=()->{inicio.await();try{service.crear(" TI ");return true;}catch(OrganizacionConflictoException e){return false;}finally{if(lock.isHeldByCurrentThread())lock.unlock();}};
    try{var a=pool.submit(tarea);var b=pool.submit(tarea);inicio.countDown();assertNotEquals(a.get(5,TimeUnit.SECONDS),b.get(5,TimeUnit.SECONDS));assertEquals(1,datos.size());}finally{pool.shutdownNow();}
  }
  @Test void dosEdicionesDeMismaVersionNoSeSobrescriben() throws Exception {
    var repo=mock(AdministracionOrganizacionRepository.class); var lock=new ReentrantLock();
    var datos=new java.util.concurrent.atomic.AtomicReference<>(new DepartamentoAdministrativo(1,"TI",true,0L));
    doAnswer(i->{lock.lock();return null;}).when(repo).bloquearEscrituras();
    when(repo.buscarDepartamento(1)).thenAnswer(i->Optional.of(datos.get()));
    when(repo.listarDepartamentos()).thenAnswer(i->List.of(datos.get()));
    when(repo.guardarDepartamento(any())).thenAnswer(i->{var d=i.getArgument(0,DepartamentoAdministrativo.class);var nuevo=new DepartamentoAdministrativo(1,d.nombre(),d.activo(),d.version()+1);datos.set(nuevo);return nuevo;});
    var service=new AdministrarOrganizacionService(repo,mock(ApplicationEventPublisher.class));var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
    Callable<Boolean> tarea=()->{inicio.await();try{service.editar(1,"Infraestructura",0L);return true;}catch(OrganizacionConflictoException e){return false;}finally{if(lock.isHeldByCurrentThread())lock.unlock();}};
    try{var a=pool.submit(tarea);var b=pool.submit(tarea);inicio.countDown();assertNotEquals(a.get(5,TimeUnit.SECONDS),b.get(5,TimeUnit.SECONDS));assertEquals(1L,datos.get().version());}finally{pool.shutdownNow();}
  }
  @Test void creacionEsperaDesactivacionYNoIntroduceContactoActivo() throws Exception {
    var repo=mock(AdministracionOrganizacionRepository.class);var lock=new ReentrantLock();var retenido=new CountDownLatch(1);var liberar=new CountDownLatch(1);
    var datos=new java.util.concurrent.atomic.AtomicReference<>(new DepartamentoAdministrativo(1,"TI",true,0L));
    doAnswer(i->{lock.lock();return null;}).when(repo).bloquearEscrituras();when(repo.buscarDepartamento(1)).thenAnswer(i->Optional.of(datos.get()));
    when(repo.guardarDepartamento(any())).thenAnswer(i->{retenido.countDown();assertTrue(liberar.await(5,TimeUnit.SECONDS));var d=i.getArgument(0,DepartamentoAdministrativo.class);datos.set(d);return d;});
    var service=new AdministrarOrganizacionService(repo,mock(ApplicationEventPublisher.class));var pool=Executors.newFixedThreadPool(2);
    try{var desactivar=pool.submit(()->{try{service.estado(1,false,0L);}finally{lock.unlock();}});assertTrue(retenido.await(5,TimeUnit.SECONDS));
      var crear=pool.submit(()->{try{return assertThrows(OrganizacionConflictoException.class,()->service.crearContacto(1,"Anexo","619",true));}finally{lock.unlock();}});
      liberar.countDown();desactivar.get(5,TimeUnit.SECONDS);crear.get(5,TimeUnit.SECONDS);verify(repo,never()).guardarContacto(any());
    }finally{liberar.countDown();pool.shutdownNow();}
  }
}
