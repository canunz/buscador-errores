package cl.casol.backend.ejecucion.infrastructure.persistence;

import cl.casol.backend.ejecucion.application.port.out.*;
import cl.casol.backend.ejecucion.application.service.GestionarEjecucionService;
import cl.casol.backend.ejecucion.domain.*;
import cl.casol.backend.ejecucion.domain.exception.*;
import cl.casol.backend.ejecucion.infrastructure.persistence.adapter.*;
import cl.casol.backend.ejecucion.infrastructure.persistence.repository.*;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.procedimiento.application.port.out.*;
import cl.casol.backend.procedimiento.domain.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** JPA/transacciones en H2 efímero. No verifica el dialecto ni el esquema MySQL del piloto. */
@SpringJUnitConfig(EjecucionContratoJpaTest.Config.class)
@org.junit.jupiter.api.Timeout(30)
class EjecucionContratoJpaTest {
    static final String EMAIL="no-login@example.invalid";
    static Usuario usuario() { return new Usuario(1,new Rol(1,"TECNICO",null,true,null,null),"QA",EMAIL,"NO_LOGIN_TEST",true,null,null); }

    @Configuration @EnableTransactionManagement
    @Import({GestionarEjecucionService.class,EjecucionRepositoryAdapter.class,EjecucionPasoRepositoryAdapter.class,
            cl.casol.backend.identidad.application.service.AdministrarSucursalesService.class,
            cl.casol.backend.identidad.infrastructure.persistence.adapter.SucursalRepositoryAdapter.class})
    static class Config {
        @Bean static org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor exceptionTranslation() {
            return new org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor();
        }
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:ejecucion_contrato;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa",""); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f=new LocalContainerEntityManagerFactoryBean(); f.setDataSource(ds); f.setPackagesToScan("cl.casol.backend");
            f.setJpaVendorAdapter(new HibernateJpaVendorAdapter()); f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop")); return f;
        }
        @Bean @Primary EntityManager entityManager(EntityManagerFactory f) { return SharedEntityManagerCreator.createSharedEntityManager(f); }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory f) { return new JpaTransactionManager(f); }
        @Bean EjecucionJpaRepository ejecuciones(EntityManager em) { return new JpaRepositoryFactory(em).getRepository(EjecucionJpaRepository.class); }
        @Bean EjecucionPasoJpaRepository pasosEjecucion(EntityManager em) { return new JpaRepositoryFactory(em).getRepository(EjecucionPasoJpaRepository.class); }
        @Bean cl.casol.backend.identidad.infrastructure.persistence.repository.SucursalJpaRepository sucursales(EntityManager em) {
            return new JpaRepositoryFactory(em).getRepository(cl.casol.backend.identidad.infrastructure.persistence.repository.SucursalJpaRepository.class);
        }
        @Bean ProcedimientoRepository procedimientos() { return mock(ProcedimientoRepository.class); }
        @Bean PasoRepository pasos() { return mock(PasoRepository.class); }
        @Bean AutomatizacionEjecucionPort automatizacion() { return mock(AutomatizacionEjecucionPort.class); }
        @Bean UsuarioRepository usuarios() {
            var r=mock(UsuarioRepository.class); when(r.buscarPorEmail(EMAIL)).thenReturn(Optional.of(usuario())); return r;
        }
    }
    @Autowired GestionarEjecucionService servicio;
    @Autowired EjecucionPasoRepository checklist;
    @Autowired EjecucionRepository ejecuciones;
    @Autowired PasoRepository definiciones;
    @Autowired PlatformTransactionManager tm;
    @Autowired DataSource ds;
    @Autowired cl.casol.backend.identidad.application.service.AdministrarSucursalesService directorio;
    JdbcTemplate jdbc;
    int ejecucionId,pasoId;
    final LocalDateTime fecha=LocalDateTime.of(2026,11,7,12,0);

    @BeforeEach void preparar() {
        jdbc=new JdbcTemplate(ds); reset(definiciones); jdbc.update("delete from u_sucursal");
        jdbc.update("delete from se_ejecucion_paso"); jdbc.update("delete from se_ejecucion");
        jdbc.update("delete from u_usuario"); jdbc.update("delete from u_rol");
        jdbc.update("insert into u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) values(1,'TECNICO',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("insert into u_usuario(usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) values(1,1,'Sintético',?,'NO_LOGIN_TEST',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",EMAIL);
        new TransactionTemplate(tm).executeWithoutResult(s->{
            var e=ejecuciones.guardar(new Ejecucion(null,10,usuario(),fecha,null,EstadoEjecucion.EN_CURSO,null)); ejecucionId=e.id();
            var p=checklist.guardar(new EjecucionPaso(null,e.id(),9,false,null,null,
                    new ContratoPaso(1,1,"Revisar conexión",true,ModoEjecucionPaso.MANUAL),EstadoEjecucionPaso.PENDIENTE,0)); pasoId=p.id();
        });
    }

    @Test void snapshotSeConservaEnBaseYNoRequiereDefinicion() {
        var d=servicio.listarPasos(ejecucionId,EMAIL).getFirst();
        assertEquals("Revisar conexión",d.instruccion()); assertTrue(d.esCritico());
        assertEquals(1,d.ejecucionPaso().contrato().version()); verifyNoInteractions(definiciones);
    }
    @Test void dosSesionesConMismaVersionSoloEscribenUnaVez() throws Exception {
        var inicio=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(2);
        Callable<Boolean> cambio=()->{inicio.await();try{servicio.actualizarPaso(ejecucionId,pasoId,true,"Verificado",0L,EMAIL);return true;}catch(ConflictoEjecucionException e){return false;}};
        try { var a=pool.submit(cambio);var b=pool.submit(cambio);inicio.countDown();
            assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            var p=servicio.listarPasos(ejecucionId,EMAIL).getFirst().ejecucionPaso(); assertTrue(p.cumplido());assertEquals(1,p.version());
        } finally { pool.shutdownNow(); }
    }
    @Test void completarYDesmarcarNoDejanEjecucionCompletaConPendientes() throws Exception {
        servicio.actualizarPaso(ejecucionId,pasoId,true,"Verificado",0L,EMAIL);
        var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var cierre=pool.submit(()->{inicio.await();try{servicio.completar(ejecucionId,EMAIL);return true;}catch(ReglaEjecucionException e){return false;}});
            var editar=pool.submit(()->{inicio.await();try{servicio.actualizarPaso(ejecucionId,pasoId,false,"Revisar",1L,EMAIL);return true;}catch(ReglaEjecucionException e){return false;}});
            inicio.countDown(); assertNotEquals(cierre.get(10,TimeUnit.SECONDS),editar.get(10,TimeUnit.SECONDS));
            var e=servicio.buscar(ejecucionId,EMAIL);var p=servicio.listarPasos(ejecucionId,EMAIL).getFirst().ejecucionPaso();
            assertFalse(e.estado()==EstadoEjecucion.COMPLETADA && !p.cumplido());
        } finally { pool.shutdownNow(); }
    }
    @Test void falloPosteriorRevierteEstadoCumplimientoYRevision() {
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(tm).executeWithoutResult(s->{
            servicio.actualizarPaso(ejecucionId,pasoId,true,"Verificado",0L,EMAIL);throw new IllegalStateException("Fallo sintético");
        }));
        var p=servicio.listarPasos(ejecucionId,EMAIL).getFirst().ejecucionPaso();
        assertFalse(p.cumplido());assertEquals(EstadoEjecucionPaso.PENDIENTE,p.estado());assertEquals(0,p.version());
    }
    @Test void filaHistoricaPermaneceSinSnapshotFabricado() {
        jdbc.update("update se_ejecucion_paso set contrato_version=null,contrato_orden=null,contrato_instruccion=null,contrato_critico=null,contrato_modo=null,estado_detallado=null where ejecucion_paso_id=?",pasoId);
        when(definiciones.buscarPorId(9)).thenReturn(Optional.of(new Paso(9,10,1,"Definición vigente",false)));
        var p=servicio.listarPasos(ejecucionId,EMAIL).getFirst();
        assertNull(p.ejecucionPaso().contrato());assertEquals("Definición vigente",p.instruccion());
        servicio.actualizarPaso(ejecucionId,pasoId,true,"Manual",0L,EMAIL);
        assertNull(jdbc.queryForObject("select contrato_version from se_ejecucion_paso where ejecucion_paso_id=?",Integer.class,pasoId));
    }
    @Test void catalogoPersisteRevisionesYConservaInactivasSinDisponibilizarlas() {
        var s=directorio.crear("QA_01","Sintética","QA","qa-sintetico-no-enviar@dimarsa.cl");assertEquals(0L,s.version());
        var editada=directorio.editar(s.id(),s.codigo(),"Nueva",s.responsableNombre(),s.responsableEmail(),s.version());assertEquals(1L,editada.version());
        assertThrows(cl.casol.backend.identidad.domain.exception.OrganizacionConflictoException.class,
                ()->directorio.editar(s.id(),s.codigo(),"Obsoleta",s.responsableNombre(),s.responsableEmail(),s.version()));
        var inactiva=directorio.estado(s.id(),false,editada.version());assertEquals(2L,inactiva.version());
        assertTrue(directorio.disponibles().isEmpty());assertEquals(1,directorio.listar().size());
    }
    @Test void unicidadDeCodigoResisteDosAltasConcurrentes() throws Exception {
        var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<Boolean> alta=()->{inicio.await();try{directorio.crear("qa_01","Sintética","QA","qa-sintetico-no-enviar@dimarsa.cl");return true;}
            catch(cl.casol.backend.identidad.domain.exception.OrganizacionConflictoException | org.springframework.dao.DataIntegrityViolationException e){return false;}};
        try {var a=pool.submit(alta);var b=pool.submit(alta);inicio.countDown();assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));assertEquals(1,directorio.listar().size());}
        finally {pool.shutdownNow();}
    }
}
