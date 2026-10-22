package cl.casol.backend.conocimiento.infrastructure.persistence;

import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.application.service.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.SolucionDuplicadaException;
import cl.casol.backend.conocimiento.infrastructure.persistence.adapter.*;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.*;
import cl.casol.backend.catalogo.infrastructure.persistence.repository.*;
import cl.casol.backend.identidad.infrastructure.persistence.repository.UsuarioJpaRepository;
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
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** JPA, bloqueos y transacciones reales en H2 efímero; no conecta a MySQL ni inicia Spring Boot. */
@SpringJUnitConfig(CopiarSolucionJpaConcurrencyTest.Config.class)
@org.junit.jupiter.api.Timeout(30)
class CopiarSolucionJpaConcurrencyTest {
    @Configuration @EnableTransactionManagement
    @Import({ConocimientoRepositoryAdapter.class,SolucionRepositoryAdapter.class,CopiarSolucionService.class,MantenerSolucionService.class})
    static class Config {
        @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:copias_jpa;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa","");}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){
            var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("cl.casol.backend");
            f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop","hibernate.show_sql","false"));return f;
        }
        @Bean @Primary EntityManager entityManager(EntityManagerFactory f){return SharedEntityManagerCreator.createSharedEntityManager(f);}
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory f){return new JpaTransactionManager(f);}
        @Bean ConocimientoJpaRepository conocimientos(EntityManager em){return new JpaRepositoryFactory(em).getRepository(ConocimientoJpaRepository.class);}
        @Bean SolucionJpaRepository soluciones(EntityManager em){return new JpaRepositoryFactory(em).getRepository(SolucionJpaRepository.class);}
        @Bean HardwareJpaRepository hardware(){return mock(HardwareJpaRepository.class);}
        @Bean SistemaJpaRepository sistemas(){return mock(SistemaJpaRepository.class);}
        @Bean ModuloJpaRepository modulos(){return mock(ModuloJpaRepository.class);}
        @Bean FrecuenciaJpaRepository frecuencias(){return mock(FrecuenciaJpaRepository.class);}
        @Bean UsuarioJpaRepository usuarios(){return mock(UsuarioJpaRepository.class);}
        @Bean ConsultarChatbotService chatbot(DataSource ds, @org.springframework.beans.factory.annotation.Qualifier("entityManager") EntityManager em, SolucionRepository soluciones){
            var jdbc=new JdbcTemplate(ds);
            var busqueda=new BusquedaConocimientoJdbcAdapter(jdbc,new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(ds),em);
            return new ConsultarChatbotService(new BuscarConocimientoService(busqueda),soluciones);
        }
        @Bean IndexarConocimientoService indexador(){return mock(IndexarConocimientoService.class);}
    }
    @Autowired ConsultarChatbotService chatbot;
    @Autowired DataSource ds; @Autowired PlatformTransactionManager tm;
    @Autowired ConocimientoRepository conocimientos; @Autowired CopiarSolucionService copiar;
    @Autowired MantenerSolucionService crear; @Autowired IndexarConocimientoService indexador;
    JdbcTemplate jdbc; int originalId;
    @BeforeEach void preparar(){
        reset(indexador);jdbc=new JdbcTemplate(ds);
        jdbc.update("delete from co_solucion");jdbc.update("delete from co_conocimiento");jdbc.update("delete from u_usuario");jdbc.update("delete from u_rol");
        jdbc.update("insert into u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) values(1,'ADMINISTRADOR',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("insert into u_usuario(usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) values(1,1,'Sintético','no-login@example.invalid','NO_LOGIN_TEST',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        for(int id:List.of(1,2))jdbc.update("insert into co_conocimiento(conocimiento_id,conocimiento_titulo,conocimiento_descripcion,conocimiento_estado,creado_por,fecha_creacion) values(?,?,'D','PUBLICADO',1,CURRENT_TIMESTAMP)",id,"K"+id);
        jdbc.update("insert into co_solucion(conocimiento_id,solucion_descripcion,solucion_tipo,solucion_orden,solucion_titulo,solucion_estado,solucion_autoservicio) values(1,'D','PASOS',1,'T',true,true)");
        originalId=jdbc.queryForObject("select solucion_id from co_solucion where conocimiento_id=1",Integer.class);
    }
    int filas(){return jdbc.queryForObject("select count(*) from co_solucion where conocimiento_id=2",Integer.class);}
    boolean intento(Callable<?> tarea)throws Exception{try{tarea.call();return true;}catch(SolucionDuplicadaException e){return false;}}
    @Test void bloqueoJpaDelDestinoImpideQueOtraTransaccionCopieHastaCommit()throws Exception{
        var retenido=new CountDownLatch(1);var liberar=new CountDownLatch(1);var intentando=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var holder=pool.submit(()->new TransactionTemplate(tm).execute(status->{conocimientos.bloquear(2);retenido.countDown();try{assertTrue(liberar.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return null;}));
            assertTrue(retenido.await(5,TimeUnit.SECONDS));
            var copia=pool.submit(()->{intentando.countDown();return copiar.copiar(1,originalId,2,false);});assertTrue(intentando.await(5,TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,()->copia.get(250,TimeUnit.MILLISECONDS));assertEquals(0,filas());
            liberar.countDown();holder.get(5,TimeUnit.SECONDS);var guardada=copia.get(5,TimeUnit.SECONDS);assertNotEquals(originalId,guardada.id());assertEquals(1,filas());
        }finally{liberar.countDown();pool.shutdownNow();}
    }
    @Test void dosCopiasSimultaneasDelMismoContenidoSoloInsertanUna()throws Exception{
        var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<Boolean> tarea=()->{inicio.await();return intento(()->copiar.copiar(1,originalId,2,false));};
        try{var a=pool.submit(tarea);var b=pool.submit(tarea);inicio.countDown();assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));assertEquals(1,filas());}finally{pool.shutdownNow();}
    }
    @Test void copiaYCreacionExistenteCompartenProteccionDeDuplicados()throws Exception{
        var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var a=pool.submit(()->{inicio.await();return intento(()->copiar.copiar(1,originalId,2,false));});var b=pool.submit(()->{inicio.await();return intento(()->crear.crear(2," d ",TipoSolucion.PASOS,1," t "));});inicio.countDown();assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));assertEquals(1,filas());}finally{pool.shutdownNow();}
    }
    @Test void copiaYCreacionDistintasNoColisionanEnOrden()throws Exception{
        var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var a=pool.submit(()->{inicio.await();return copiar.copiar(1,originalId,2,false);});var b=pool.submit(()->{inicio.await();return crear.crear(2,"Distinta",TipoSolucion.PASOS,1,"Distinta");});inicio.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);assertEquals(List.of(1,2),jdbc.queryForList("select solucion_orden from co_solucion where conocimiento_id=2 order by solucion_orden",Integer.class));}finally{pool.shutdownNow();}
    }
    @Test void falloDeIndiceRevierteInsercionConTransaccionReal(){doThrow(new IllegalStateException("índice no disponible")).when(indexador).indexar(2);assertThrows(IllegalStateException.class,()->copiar.copiar(1,originalId,2,false));assertEquals(0,filas());}

    @Test void copiaPrivadaNoApareceEnChatbotConConsultaReal(){copiar.copiar(1,originalId,2,false);assertTrue(chatbot.consultar("K2").isEmpty());}
    @Test void copiaPublicaActivaConfirmadaApareceEnChatbot(){copiar.copiar(1,originalId,2,true);var resultados=chatbot.consultar("K2");assertEquals(1,resultados.size());assertEquals("D",resultados.getFirst().solucion());}
    @Test void copiaInactivaAunqueAutoservicioNoApareceEnChatbot(){
        jdbc.update("insert into co_solucion(conocimiento_id,solucion_descripcion,solucion_tipo,solucion_orden,solucion_titulo,solucion_estado,solucion_autoservicio) values(2,'Privada','PASOS',4,'Otra',true,false)");
        jdbc.update("update co_solucion set solucion_estado=false where solucion_id=?",originalId);
        var copia=copiar.copiar(1,originalId,2,true);assertFalse(copia.activo());assertTrue(chatbot.consultar("K2").isEmpty());
    }
    @Test void copiaAutoservicioEnBorradorNoApareceEnChatbot(){jdbc.update("update co_conocimiento set conocimiento_estado='BORRADOR' where conocimiento_id=2");copiar.copiar(1,originalId,2,true);assertTrue(chatbot.consultar("K2").isEmpty());}
}
