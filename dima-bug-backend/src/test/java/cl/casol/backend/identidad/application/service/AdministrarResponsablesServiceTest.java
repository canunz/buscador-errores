package cl.casol.backend.identidad.application.service;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.domain.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AdministrarResponsablesServiceTest {
    final AdministracionResponsableRepository repo=mock(AdministracionResponsableRepository.class);
    final AdministracionOrganizacionRepository org=mock(AdministracionOrganizacionRepository.class);
    final ApplicationEventPublisher eventos=mock(ApplicationEventPublisher.class);
    final AdministrarResponsablesService service=new AdministrarResponsablesService(repo,org,eventos);
    final Responsable r=new Responsable(4,1,"Ana","Técnica","00123",true);
    String revision(){return ResponsableAdministrativo.from(r).revision();}
    @BeforeEach void preparar(){
        when(org.buscarDepartamento(1)).thenReturn(Optional.of(new DepartamentoAdministrativo(1,"TI",true,2L)));
        when(repo.bloquear(4)).thenReturn(Optional.of(r));
        when(repo.guardar(any())).thenAnswer(i->i.getArgument(0));
    }
    @Test void listaAdministrativaIncluyeInactivos(){when(repo.listar(1)).thenReturn(List.of(new Responsable(4,1,"Ana",null,null,false)));assertFalse(service.listar(1).getFirst().activo());}
    @Test void listadoGlobalConservaHistoricosSinDepartamento(){when(repo.listar(null)).thenReturn(List.of(new Responsable(4,null,"Histórico",null,null,false)));assertNull(service.listar(null).getFirst().departamentoId());}
    @Test void detalleIncluyeHuella(){when(repo.buscar(4)).thenReturn(Optional.of(r));assertEquals(64,service.buscar(4).revision().length());}
    @Test void detalleInexistente404(){assertThrows(ResponsableNoEncontradoException.class,()->service.buscar(9));}
    @Test void crearExigeDepartamento(){assertThrows(OrganizacionInvalidaException.class,()->service.crear(null,"Ana",null,null));verify(repo,never()).guardar(any());}
    @Test void crearValidaExistenciaDelDepartamento(){assertThrows(DepartamentoNoEncontradoException.class,()->service.crear(9,"Ana",null,null));}
    @Test void crearNormalizaCamposYConservaContactoComoTexto(){var nuevo=service.crear(1," Ana "," "," 00123 ");assertEquals("Ana",nuevo.nombre());assertNull(nuevo.cargo());assertEquals("00123",nuevo.contacto());verify(eventos).publishEvent(new OrganizacionActualizada(1));}
    @Test void noCreaEnDepartamentoInactivo(){when(org.buscarDepartamento(1)).thenReturn(Optional.of(new DepartamentoAdministrativo(1,"TI",false,2L)));assertThrows(OrganizacionConflictoException.class,()->service.crear(1,"Ana",null,null));verify(repo,never()).guardar(any());}
    @Test void limitesDeCampos(){assertThrows(OrganizacionInvalidaException.class,()->service.crear(1," ",null,null));assertThrows(OrganizacionInvalidaException.class,()->service.crear(1,"a".repeat(161),null,null));assertThrows(OrganizacionInvalidaException.class,()->service.crear(1,"Ana","c".repeat(121),null));assertThrows(OrganizacionInvalidaException.class,()->service.crear(1,"Ana",null,"x".repeat(161)));}
    @Test void editarConservaPertenenciaEstadoYReferencias(){var cambiado=service.editar(4,"Bea",null,"00456",revision());assertEquals(1,cambiado.departamentoId());assertTrue(cambiado.activo());assertNotEquals(revision(),cambiado.revision());verify(eventos).publishEvent(new OrganizacionActualizada(1));}
    @Test void conflictoRechazaEdicionYNoPublicaEvento(){assertThrows(OrganizacionConflictoException.class,()->service.editar(4,"Bea",null,null,"0".repeat(64)));verify(repo,never()).guardar(any());verifyNoInteractions(eventos);}
    @Test void conflictoRechazaCambioDeEstado(){assertThrows(OrganizacionConflictoException.class,()->service.estado(4,false,"0".repeat(64)));verify(repo,never()).guardar(any());}
    @Test void huellaIncluyeTodosLosCamposYNullDifiereDeVacio(){assertNotEquals(revision(),ResponsableAdministrativo.from(new Responsable(4,1,"Ana","Técnica","00123",false)).revision());assertNotEquals(ResponsableAdministrativo.from(new Responsable(4,1,"Ana",null,null,true)).revision(),ResponsableAdministrativo.from(new Responsable(4,1,"Ana","",null,true)).revision());}
    @Test void desactivaSinBorrarNiCambiarDepartamento(){assertFalse(service.estado(4,false,revision()).activo());verify(eventos).publishEvent(new OrganizacionActualizada(1));}
    @Test void noReactivaBajoDepartamentoInactivo(){var inactivo=new Responsable(4,1,"Ana",null,null,false);when(repo.bloquear(4)).thenReturn(Optional.of(inactivo));when(org.buscarDepartamento(1)).thenReturn(Optional.of(new DepartamentoAdministrativo(1,"TI",false,2L)));assertThrows(OrganizacionConflictoException.class,()->service.estado(4,true,ResponsableAdministrativo.from(inactivo).revision()));verify(repo,never()).guardar(any());}
    @Test void editarHistoricoSinDepartamentoSeBloqueaSinAsignarlo(){var sinPadre=new Responsable(4,null,"Histórico",null,null,false);when(repo.bloquear(4)).thenReturn(Optional.of(sinPadre));assertThrows(OrganizacionConflictoException.class,()->service.editar(4,"Ana",null,null,ResponsableAdministrativo.from(sinPadre).revision()));verify(repo,never()).guardar(any());}
    @Test void editaYDesactivaAunqueDepartamentoEsteInactivo(){when(org.buscarDepartamento(1)).thenReturn(Optional.of(new DepartamentoAdministrativo(1,"TI",false,2L)));assertEquals("Bea",service.editar(4,"Bea",null,null,revision()).nombre());assertFalse(service.estado(4,false,revision()).activo());}
    // Simula exclusión del puerto; no conecta a InnoDB ni sustituye una validación QA transaccional.
    @Test void dosSesionesConMismaHuellaSoloPermitenUnGanador() throws Exception {
        var lock=new ReentrantLock();var actual=new AtomicReference<>(r);var inicio=new CountDownLatch(1);
        doAnswer(i->{lock.lock();return null;}).when(org).bloquearEscrituras();
        when(repo.bloquear(4)).thenAnswer(i->Optional.of(actual.get()));
        when(repo.guardar(any())).thenAnswer(i->{Responsable v=i.getArgument(0);actual.set(v);return v;});
        var pool=Executors.newFixedThreadPool(2);
        Callable<Boolean> tarea=()->{inicio.await();try{service.editar(4,"Bea",null,null,revision());return true;}catch(OrganizacionConflictoException e){return false;}finally{if(lock.isHeldByCurrentThread())lock.unlock();}};
        try{var a=pool.submit(tarea);var b=pool.submit(tarea);inicio.countDown();assertNotEquals(a.get(5,TimeUnit.SECONDS),b.get(5,TimeUnit.SECONDS));verify(repo,times(1)).guardar(any());}finally{pool.shutdownNow();}
    }
}
