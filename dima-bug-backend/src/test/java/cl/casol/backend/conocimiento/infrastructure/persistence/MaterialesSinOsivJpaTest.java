package cl.casol.backend.conocimiento.infrastructure.persistence;

import cl.casol.backend.catalogo.infrastructure.persistence.repository.*;
import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.application.service.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.infrastructure.persistence.adapter.ConocimientoRepositoryAdapter;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.ConocimientoJpaRepository;
import cl.casol.backend.identidad.infrastructure.persistence.repository.UsuarioJpaRepository;
import cl.casol.backend.shared.application.archivo.*;
import jakarta.persistence.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** JPA real y sesiones cerradas; H2 efímero, sin contexto web ni transacción envolvente de test. */
@SpringJUnitConfig(MaterialesSinOsivJpaTest.Config.class)
@TestPropertySource(properties = "spring.jpa.open-in-view=false")
class MaterialesSinOsivJpaTest {
    @Configuration @EnableTransactionManagement @Import(ConocimientoRepositoryAdapter.class)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:materiales_sin_osiv;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f = new LocalContainerEntityManagerFactoryBean();
            f.setDataSource(ds); f.setPackagesToScan("cl.casol.backend");
            f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.generate_statistics", "true"));
            return f;
        }
        @Bean @Primary EntityManager entityManager(EntityManagerFactory f) { return SharedEntityManagerCreator.createSharedEntityManager(f); }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory f) { return new JpaTransactionManager(f); }
        @Bean ConocimientoJpaRepository conocimientos(EntityManager em) { return new JpaRepositoryFactory(em).getRepository(ConocimientoJpaRepository.class); }
        @Bean HardwareJpaRepository hardware() { return mock(HardwareJpaRepository.class); }
        @Bean SistemaJpaRepository sistemas() { return mock(SistemaJpaRepository.class); }
        @Bean ModuloJpaRepository modulos() { return mock(ModuloJpaRepository.class); }
        @Bean FrecuenciaJpaRepository frecuencias() { return mock(FrecuenciaJpaRepository.class); }
        @Bean UsuarioJpaRepository usuarios() { return mock(UsuarioJpaRepository.class); }
    }
    @Autowired ConocimientoRepository conocimientos;
    @Autowired EntityManagerFactory emf;
    @Autowired DataSource ds;
    MaterialApoyoRepository materiales;
    ArchivoMaterialService archivos;
    MaterialConocimientoArchivoService service;
    final MaterialApoyo material = new MaterialApoyo(10, 1, null, "QA PDF", TipoMaterial.PDF, "file:materiales/sintetico");

    @BeforeEach void preparar() {
        var j = new JdbcTemplate(ds);
        for (String table : List.of("co_conocimiento", "cl_modulo", "cl_hardware", "cl_sistema", "cl_frecuencia", "u_usuario", "u_rol"))
            j.update("delete from " + table);
        j.update("insert into u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) values(1,'ADMINISTRADOR',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        for (int id : List.of(1,2))
            j.update("insert into u_usuario(usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) values(?,1,?,?,'NO_LOGIN',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,"QA "+id,"qa"+id+"@example.invalid");
        j.update("insert into cl_hardware(hardware_id,hardware_nombre,hardware_sistema_operativo,hardware_estado) values(1,'QA equipo','QA OS',true)");
        j.update("insert into cl_sistema(sistema_id,sistema_nombre,sistema_estado) values(1,'QA sistema',true)");
        j.update("insert into cl_modulo(modulo_id,sistema_id,modulo_nombre,modulo_estado) values(1,1,'QA módulo',true)");
        j.update("insert into cl_frecuencia(frecuencia_id,frecuencia_nombre,frecuencia_orden,frecuencia_estado) values(1,'QA frecuencia',1,true)");
        j.update("insert into co_conocimiento(conocimiento_id,conocimiento_titulo,conocimiento_descripcion,conocimiento_estado,hardware_id,sistema_id,modulo_id,frecuencia_id,creado_por,modificado_por,fecha_creacion,fecha_modificacion) values(1,'QA conocimiento','QA descripción','BORRADOR',1,1,1,1,1,2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        materiales = mock(MaterialApoyoRepository.class);
        archivos = mock(ArchivoMaterialService.class);
        service = new MaterialConocimientoArchivoService(conocimientos, materiales, mock(MantenerMaterialApoyoService.class), archivos, mock(IndexarConocimientoService.class));
        assertFalse(TransactionSynchronizationManager.hasResource(emf));
        emf.unwrap(SessionFactory.class).getStatistics().clear();
    }
    @Test void cargaTodasLasRelacionesEnUnaConsultaSinSesionWeb() {
        var c = conocimientos.buscarPorId(1).orElseThrow();
        assertEquals("QA 1", c.getCreadoPor().getNombre());
        assertEquals("ADMINISTRADOR", c.getCreadoPor().getRol().getNombre());
        assertEquals("QA 2", c.getModificadoPor().getNombre());
        assertEquals("QA equipo", c.getHardware().nombre());
        assertEquals("QA sistema", c.getSistema().nombre());
        assertEquals(1, c.getModulo().sistemaId());
        assertEquals("QA frecuencia", c.getFrecuencia().nombre());
        assertEquals(1, emf.unwrap(SessionFactory.class).getStatistics().getPrepareStatementCount());
        assertFalse(TransactionSynchronizationManager.hasResource(emf));
    }
    @Test void subidaVerificaPadreConJpaRealSinOsiv() {
        when(archivos.crear(anyString(), any(), any(), any())).thenReturn(material);
        assertEquals(material, service.subir(1, "QA PDF", TipoMaterial.PDF, new ArchivoSubido("application/pdf", new byte[]{37,80,68,70,45})));
        verify(archivos).crear(anyString(), eq(TipoMaterial.PDF), any(), any());
    }
    @Test void descargaVerificaPadreConJpaRealSinOsiv() {
        when(materiales.buscarPorId(10)).thenReturn(Optional.of(material));
        when(archivos.descargar(material)).thenReturn(new ArchivoDescarga("qa.pdf", "application/pdf", new byte[]{1,2}));
        assertArrayEquals(new byte[]{1,2}, service.descargar(1,10).contenido());
    }
    @Test void consultaInexistenteSigueVacia() { assertTrue(conocimientos.buscarPorId(999).isEmpty()); }
    @Test void eliminadoSigueExcluidoPeroConsultaAdministrativaLoConserva() {
        new JdbcTemplate(ds).update("update co_conocimiento set conocimiento_estado='ELIMINADO' where conocimiento_id=1");
        assertTrue(conocimientos.buscarPorId(1).isEmpty());
        assertEquals(EstadoConocimiento.ELIMINADO, conocimientos.buscarPorIdIncluidoEliminado(1).orElseThrow().getEstado());
    }
}
