package cl.casol.backend.ejecucion.infrastructure.persistence;

import cl.casol.backend.ejecucion.application.port.out.*;
import cl.casol.backend.ejecucion.application.service.*;
import cl.casol.backend.ejecucion.domain.*;
import cl.casol.backend.ejecucion.domain.exception.*;
import cl.casol.backend.ejecucion.infrastructure.automatizacion.*;
import cl.casol.backend.ejecucion.infrastructure.persistence.adapter.*;
import cl.casol.backend.ejecucion.infrastructure.persistence.repository.*;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.application.service.AdministrarSucursalesService;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.infrastructure.persistence.adapter.SucursalRepositoryAdapter;
import cl.casol.backend.identidad.infrastructure.persistence.repository.*;
import cl.casol.backend.procedimiento.application.port.out.*;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.procedimiento.infrastructure.persistence.adapter.*;
import cl.casol.backend.procedimiento.infrastructure.persistence.repository.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Flujo real de servicios, repositorios y transacciones sobre H2 efímero; ningún correo o MySQL real. */
@SpringJUnitConfig(AutomatizacionFlujoJpaTest.Config.class)
@org.junit.jupiter.api.Timeout(30)
class AutomatizacionFlujoJpaTest {
    static final String TECNICO="qa-tecnico@example.invalid",OTRO="qa-otro@example.invalid";
    static Usuario usuario(int id) { return new Usuario(id,new Rol(1,"TECNICO",null,true,null,null),"QA",id==1?TECNICO:OTRO,"NO_LOGIN_TEST",true,null,null); }
    @Configuration @EnableTransactionManagement(proxyTargetClass=true)
    @Import({GestionarEjecucionService.class,AutomatizarEvidenciaService.class,EjecucionRepositoryAdapter.class,EjecucionPasoRepositoryAdapter.class,
            SolicitudEvidenciaRepositoryAdapter.class,SucursalRepositoryAdapter.class,AdministrarSucursalesService.class,
            ProcedimientoRepositoryAdapter.class,PasoRepositoryAdapter.class,FotosEvidenciaAdapter.class,cl.casol.backend.procedimiento.application.service.ConfigurarPasoService.class})
    static class Config {
        @Bean static org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor exceptionTranslation(){return new org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor();}
        @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:automatizacion_workflow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa","");}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("cl.casol.backend");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop"));return f;}
        @Bean @Primary EntityManager entityManager(EntityManagerFactory f){return SharedEntityManagerCreator.createSharedEntityManager(f);}
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory f){return new JpaTransactionManager(f);}
        @Bean EjecucionJpaRepository ejecuciones(EntityManager em){return new JpaRepositoryFactory(em).getRepository(EjecucionJpaRepository.class);}
        @Bean EjecucionPasoJpaRepository instancias(EntityManager em){return new JpaRepositoryFactory(em).getRepository(EjecucionPasoJpaRepository.class);}
        @Bean SucursalJpaRepository sucursales(EntityManager em){return new JpaRepositoryFactory(em).getRepository(SucursalJpaRepository.class);}
        @Bean ProcedimientoJpaRepository procedimientos(EntityManager em){return new JpaRepositoryFactory(em).getRepository(ProcedimientoJpaRepository.class);}
        @Bean PasoJpaRepository pasos(EntityManager em){return new JpaRepositoryFactory(em).getRepository(PasoJpaRepository.class);}
        @Bean UsuarioJpaRepository usuariosJpa(EntityManager em){return new JpaRepositoryFactory(em).getRepository(UsuarioJpaRepository.class);}
        @Bean UsuarioRepository usuarios(){var r=mock(UsuarioRepository.class);when(r.buscarPorEmail(TECNICO)).thenReturn(Optional.of(usuario(1)));when(r.buscarPorEmail(OTRO)).thenReturn(Optional.of(usuario(2)));when(r.buscarPorEmail("qa-admin@example.invalid")).thenReturn(Optional.of(new Usuario(3,new Rol(2,"ADMINISTRADOR",null,true,null,null),"QA admin","qa-admin@example.invalid","NO_LOGIN_TEST",true,null,null)));return r;}
        @Bean Clock reloj(){return Clock.fixed(Instant.parse("2026-11-07T15:00:00Z"),ZoneOffset.UTC);}
        @Bean CorreoEvidenciaPort correo(){return new CorreoSimuladoAdapter("SIMULADO",new StandardEnvironment());}
    }
    @Autowired GestionarEjecucionService ejecutar;
    @Autowired AutomatizarEvidenciaService automatizar;
    @Autowired AdministrarSucursalesService directorio;
    @Autowired ProcedimientoRepository procedimientos;
    @Autowired PasoRepository pasos;
    @Autowired SolicitudEvidenciaRepository solicitudes;
    @Autowired cl.casol.backend.procedimiento.application.service.ConfigurarPasoService configurar;
    @Autowired FotosEvidenciaPort fotos;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean(name="correo") CorreoEvidenciaPort correo;
    @Autowired PlatformTransactionManager tm;
    @Autowired DataSource ds;
    @Autowired EntityManagerFactory emf;
    JdbcTemplate jdbc;int procedimientoId,sucursalId;
    @BeforeEach void preparar() throws Exception {
        try(var c=ds.getConnection()){assertTrue(c.getMetaData().getURL().startsWith("jdbc:h2:mem:automatizacion_workflow"));}
        jdbc=new JdbcTemplate(ds);
        for(String tabla:List.of("se_evento_ejecucion","se_evidencia","se_solicitud_evidencia","se_ejecucion_paso","se_ejecucion","pr_paso","pr_procedimiento","u_sucursal","u_usuario","u_rol"))jdbc.update("delete from "+tabla);
        jdbc.update("insert into u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) values(1,'TECNICO',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        for(int id:List.of(1,2))jdbc.update("insert into u_usuario(usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) values(?,1,'QA',?,'NO_LOGIN_TEST',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,id==1?TECNICO:OTRO);
        jdbc.update("insert into u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) values(2,'ADMINISTRADOR',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("insert into u_usuario(usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) values(3,2,'QA admin','qa-admin@example.invalid','NO_LOGIN_TEST',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        sucursalId=directorio.crear("QA_01","Sucursal sintética","Responsable de prueba","qa-sintetico-no-enviar@dimarsa.cl").id();
        new TransactionTemplate(tm).executeWithoutResult(status->{
            var p=procedimientos.guardar(new Procedimiento(null,"Preparar POS · prueba",null,EstadoProcedimiento.PUBLICADO,usuario(1),LocalDateTime.now(),null,null));procedimientoId=p.id();
            pasos.guardar(new Paso(null,p.id(),1,"Verificar mueble, red y electricidad",true,ModoEjecucionPaso.VALIDACION_EXTERNA,"Tres fotografías para revisión"));
            pasos.guardar(new Paso(null,p.id(),2,"Trabajo físico manual",true));
        });
    }
    Ejecucion iniciar(){return ejecutar.iniciar(procedimientoId,sucursalId,UUID.randomUUID().toString(),TECNICO);}
    SolicitudEvidencia actual(int id){return automatizar.listar(id,TECNICO).stream().max(Comparator.comparingInt(SolicitudEvidencia::ronda)).orElseThrow();}
    Map<EvidenciaFotografica.Categoria,byte[]> imagenes(){return automatizar.muestras();}
    SolicitudEvidencia recibir(Ejecucion e){var s=actual(e.id());return automatizar.recibirSimulada(e.id(),s.id(),s.contexto().responsableEmail(),UUID.randomUUID().toString(),imagenes(),TECNICO);}
    long contar(String tabla){return jdbc.queryForObject("select count(*) from "+tabla,Long.class);}
    @Test void flujoCompletoPersisteSolicitudFotosAprobacionYHabilitaManual() {
        var e=iniciar();var s=actual(e.id());assertEquals(SolicitudEvidencia.Estado.ESPERANDO_EVIDENCIA,s.estado());assertTrue(s.mensajeEnviadoId().startsWith("SIMULADO-"));
        assertEquals(sucursalId,s.contexto().sucursalId());assertEquals("Sucursal sintética",s.contexto().sucursalNombre());assertNotNull(s.proximoSeguimiento());
        var r=recibir(e);assertEquals(3,contar("se_evidencia"));assertFalse(ejecutar.listarPasos(e.id(),TECNICO).getFirst().ejecucionPaso().cumplido());
        assertTrue(automatizar.eventos(e.id(),TECNICO).stream().anyMatch(v->v.tipo().equals("TECNICO_NOTIFICADO")));
        automatizar.decidir(e.id(),r.id(),r.version(),true,false,null,TECNICO);
        var checklist=ejecutar.listarPasos(e.id(),TECNICO);assertTrue(checklist.getFirst().ejecucionPaso().cumplido());assertFalse(checklist.get(1).ejecucionPaso().cumplido());
        ejecutar.actualizarPaso(e.id(),checklist.get(1).ejecucionPaso().id(),true,"Trabajo comprobado",checklist.get(1).ejecucionPaso().version(),TECNICO);
        assertEquals(EstadoEjecucion.COMPLETADA,ejecutar.completar(e.id(),TECNICO).estado());
    }
    @Test void inicioRepetidoConservaUnaSolaEjecucionYSolicitud() {
        String clave=UUID.randomUUID().toString();var a=ejecutar.iniciar(procedimientoId,sucursalId,clave,TECNICO);var b=ejecutar.iniciar(procedimientoId,sucursalId,clave,TECNICO);
        assertEquals(a.id(),b.id());assertEquals(1,contar("se_ejecucion"));assertEquals(1,contar("se_solicitud_evidencia"));
    }
    @Test void dobleInicioConcurrenteRetornaLaMismaEjecucion() throws Exception {
        String clave=UUID.randomUUID().toString();var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<Integer> tarea=()->{inicio.await();return ejecutar.iniciar(procedimientoId,sucursalId,clave,TECNICO).id();};
        try{var a=pool.submit(tarea);var b=pool.submit(tarea);inicio.countDown();assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));assertEquals(1,contar("se_ejecucion"));}finally{pool.shutdownNow();}
    }
    @Test void inicioSinSucursalCapturaPasosSinCrearCorreos() {var e=ejecutar.iniciar(procedimientoId,TECNICO);assertEquals(2,ejecutar.listarPasos(e.id(),TECNICO).size());assertEquals(1,contar("se_ejecucion"));assertEquals(0,contar("se_solicitud_evidencia"));}
    @Test void sucursalInactivaNoEmiteSolicitud() {var s=directorio.buscar(sucursalId);directorio.estado(s.id(),false,s.version());assertThrows(ReglaEjecucionException.class,this::iniciar);assertEquals(0,contar("se_ejecucion"));}
    @Test void noSePuedeSaltarUnPasoExternoNiCompletarDesdePatch() {
        var e=iniciar();var p=ejecutar.listarPasos(e.id(),TECNICO);
        assertThrows(ConflictoEjecucionException.class,()->ejecutar.actualizarPaso(e.id(),p.getFirst().ejecucionPaso().id(),true,null,TECNICO));
        assertThrows(ConflictoEjecucionException.class,()->ejecutar.actualizarPaso(e.id(),p.get(1).ejecucionPaso().id(),true,null,TECNICO));
        assertThrows(ReglaEjecucionException.class,()->ejecutar.completar(e.id(),TECNICO));
    }
    Map<EvidenciaFotografica.Categoria,byte[]> imagenesManuales(){var datos=new EnumMap<EvidenciaFotografica.Categoria,byte[]>(EvidenciaFotografica.Categoria.class);for(var c:List.of(EvidenciaFotografica.Categoria.MUEBLE,EvidenciaFotografica.Categoria.RED,EvidenciaFotografica.Categoria.ELECTRICIDAD))datos.put(c,fotos.muestra(c));return datos;}
    @Test void recepcionManualConTransporteRealRequiereAprobacionYConservaAuditoria() {
        var e=iniciar();var s=actual(e.id());doReturn(false).when(correo).simulado();
        clearInvocations(correo);
        var r=automatizar.recibir(e.id(),s.id(),s.contexto().responsableEmail(),UUID.randomUUID().toString(),imagenesManuales(),TECNICO);
        assertEquals(SolicitudEvidencia.Estado.EVIDENCIA_RECIBIDA,r.estado());
        assertFalse(ejecutar.listarPasos(e.id(),TECNICO).getFirst().ejecucionPaso().cumplido());
        automatizar.decidir(e.id(),r.id(),r.version(),true,false,null,TECNICO);
        assertTrue(ejecutar.listarPasos(e.id(),TECNICO).getFirst().ejecucionPaso().cumplido());
        var eventos=automatizar.eventos(e.id(),TECNICO);
        assertTrue(eventos.stream().anyMatch(v->v.tipo().equals("EVIDENCIA_RECIBIDA") && v.detalle().contains("manual") && v.fecha()!=null));
        assertTrue(eventos.stream().anyMatch(v->v.tipo().equals("EVIDENCIA_APROBADA")));
        assertEquals(3,contar("se_evidencia"));verify(correo,never()).enviar(any(),anyInt());
    }
    @Test void recepcionManualNoPermiteAjenoArchivosInvalidosNiDuplicados() {
        var e=iniciar();var s=actual(e.id());doReturn(false).when(correo).simulado();
        assertThrows(AccessDeniedException.class,()->automatizar.recibir(e.id(),s.id(),s.contexto().responsableEmail(),UUID.randomUUID().toString(),imagenesManuales(),OTRO));
        var invalidas=imagenesManuales();invalidas.put(EvidenciaFotografica.Categoria.RED,new byte[]{1,2});
        assertThrows(ReglaEjecucionException.class,()->automatizar.recibir(e.id(),s.id(),s.contexto().responsableEmail(),UUID.randomUUID().toString(),invalidas,TECNICO));
        assertEquals(0,contar("se_evidencia"));
        String clave=UUID.randomUUID().toString();var f=imagenesManuales();
        automatizar.recibir(e.id(),s.id(),s.contexto().responsableEmail(),clave,f,TECNICO);
        automatizar.recibir(e.id(),s.id(),s.contexto().responsableEmail(),clave,f,TECNICO);
        assertEquals(3,contar("se_evidencia"));
        assertEquals(1,automatizar.eventos(e.id(),TECNICO).stream().filter(v->v.tipo().equals("EVIDENCIA_RECIBIDA")).count());
        assertThrows(ReglaEjecucionException.class,()->automatizar.recibirSimulada(e.id(),s.id(),s.contexto().responsableEmail(),UUID.randomUUID().toString(),f,TECNICO));
    }
    @Test void rechazoManualEsperaSolicitudExplicitaYNoDuplicaRondas() {
        var e=iniciar();var s=actual(e.id());var imagenes=imagenesManuales();
        doReturn(false).when(correo).simulado();
        var recibida=automatizar.recibir(e.id(),s.id(),s.contexto().responsableEmail(),UUID.randomUUID().toString(),imagenes,TECNICO);
        clearInvocations(correo);
        var rechazada=automatizar.decidir(e.id(),s.id(),recibida.version(),false,false,"Fotografía borrosa",TECNICO,false);
        assertEquals(SolicitudEvidencia.Estado.RECHAZADA,actual(e.id()).estado());assertEquals(1,contar("se_solicitud_evidencia"));
        verify(correo,never()).enviar(any(),anyInt());assertEquals(3,contar("se_evidencia"));
        assertTrue(automatizar.eventos(e.id(),TECNICO).stream().anyMatch(v->v.tipo().equals("EVIDENCIA_RECHAZADA") && v.actorId()==1 && v.fecha()!=null && v.detalle().equals("Fotografía borrosa")));
        assertThrows(AccessDeniedException.class,()->automatizar.solicitarNuevamente(e.id(),s.id(),rechazada.version(),OTRO));
        var nueva=automatizar.solicitarNuevamente(e.id(),s.id(),rechazada.version(),TECNICO);
        assertEquals(2,nueva.ronda());assertEquals(nueva.id(),automatizar.solicitarNuevamente(e.id(),s.id(),rechazada.version(),TECNICO).id());
        assertEquals(2,contar("se_solicitud_evidencia"));assertEquals(3,contar("se_evidencia"));
    }
    @Test void recepcionManualDistingueInexistenciaYPertenenciaSinEscribirNiEnviar() {
        var e=iniciar();var s=actual(e.id());var otra=iniciar();var ajena=actual(otra.id());var archivos=imagenesManuales();
        clearInvocations(correo);
        assertThrows(EjecucionNoEncontradaException.class,()->automatizar.recibir(Integer.MAX_VALUE,s.id(),s.contexto().responsableEmail(),UUID.randomUUID().toString(),archivos,TECNICO));
        assertThrows(AccessDeniedException.class,()->automatizar.recibir(e.id(),UUID.randomUUID().toString(),s.contexto().responsableEmail(),UUID.randomUUID().toString(),archivos,TECNICO));
        assertThrows(AccessDeniedException.class,()->automatizar.recibir(e.id(),ajena.id(),ajena.contexto().responsableEmail(),UUID.randomUUID().toString(),archivos,TECNICO));
        assertEquals(0,contar("se_evidencia"));
        assertEquals(SolicitudEvidencia.Estado.ESPERANDO_EVIDENCIA,actual(e.id()).estado());
        assertEquals(SolicitudEvidencia.Estado.ESPERANDO_EVIDENCIA,actual(otra.id()).estado());
        verify(correo,never()).enviar(any(),anyInt());
    }
    @Test void remitenteFalsoNoAsociaFotos() {var e=iniciar();var s=actual(e.id());assertThrows(AccessDeniedException.class,()->automatizar.recibirSimulada(e.id(),s.id(),"otro@dimarsa.cl",UUID.randomUUID().toString(),imagenes(),TECNICO));assertEquals(0,contar("se_evidencia"));}
    @Test void fotoInvalidaRevierteTodoElConjunto() {var e=iniciar();var s=actual(e.id());var f=imagenes();f.put(EvidenciaFotografica.Categoria.RED,new byte[]{1,2,3});assertThrows(ReglaEjecucionException.class,()->automatizar.recibirSimulada(e.id(),s.id(),s.contexto().responsableEmail(),UUID.randomUUID().toString(),f,TECNICO));assertEquals(0,contar("se_evidencia"));assertEquals(SolicitudEvidencia.Estado.ESPERANDO_EVIDENCIA,actual(e.id()).estado());}
    @Test void mensajeDuplicadoNoDuplicaFotosNiEventos() {var e=iniciar();var s=actual(e.id());String msg=UUID.randomUUID().toString();var f=imagenes();automatizar.recibirSimulada(e.id(),s.id(),s.contexto().responsableEmail(),msg,f,TECNICO);long eventos=contar("se_evento_ejecucion");automatizar.recibirSimulada(e.id(),s.id(),s.contexto().responsableEmail(),msg,f,TECNICO);assertEquals(3,contar("se_evidencia"));assertEquals(eventos,contar("se_evento_ejecucion"));}
    @Test void rechazoGeneraUnaNuevaRondaSinBorrarFotos() {var e=iniciar();var r=recibir(e);automatizar.decidir(e.id(),r.id(),r.version(),false,false,"No se ve la toma eléctrica",TECNICO);var nueva=actual(e.id());assertEquals(2,nueva.ronda());assertNotEquals(r.id(),nueva.id());assertEquals(SolicitudEvidencia.Estado.ESPERANDO_EVIDENCIA,nueva.estado());assertEquals(3,contar("se_evidencia"));assertEquals(EstadoEjecucionPaso.ESPERANDO_EVIDENCIA,ejecutar.listarPasos(e.id(),TECNICO).getFirst().ejecucionPaso().estado());assertThrows(ConflictoEjecucionException.class,()->automatizar.decidir(e.id(),r.id(),r.version(),true,false,null,TECNICO));}
    @Test void rechazoDefinitivoDetieneElSeguimiento() {var e=iniciar();var r=recibir(e);automatizar.decidir(e.id(),r.id(),r.version(),false,true,"Instalación no disponible",TECNICO);assertEquals(1,contar("se_solicitud_evidencia"));assertNull(actual(e.id()).proximoSeguimiento());automatizar.simularTiempo(e.id(),r.id(),120,TECNICO);assertFalse(automatizar.eventos(e.id(),TECNICO).stream().anyMatch(v->v.tipo().equals("ESCALADO_AL_TECNICO")));}
    @Test void recordatoriosPersistenYNoSeDuplican() {var e=iniciar();var s=actual(e.id());for(int hora:List.of(24,24,72,72,120,120))automatizar.simularTiempo(e.id(),s.id(),hora,TECNICO);var eventos=automatizar.eventos(e.id(),TECNICO);assertEquals(2,eventos.stream().filter(v->v.tipo().equals("RECORDATORIO_SIMULADO")).count());assertEquals(1,eventos.stream().filter(v->v.tipo().equals("ESCALADO_AL_TECNICO")).count());assertNull(actual(e.id()).proximoSeguimiento());}
    @Test void recuperacionNoEmiteRafagaDeRecordatoriosVencidos() {var e=iniciar();var s=actual(e.id());automatizar.simularTiempo(e.id(),s.id(),72,TECNICO);assertEquals(1,automatizar.eventos(e.id(),TECNICO).stream().filter(v->v.tipo().equals("RECORDATORIO_SIMULADO")).count());assertEquals(72,actual(e.id()).reglaAplicada());}
    @Test void recibirFotosDetieneRecordatoriosAntesDeAprobar() {var e=iniciar();var r=recibir(e);automatizar.simularTiempo(e.id(),r.id(),120,TECNICO);assertNull(actual(e.id()).proximoSeguimiento());assertEquals(0,actual(e.id()).reglaAplicada());assertFalse(ejecutar.listarPasos(e.id(),TECNICO).getFirst().ejecucionPaso().cumplido());}
    @Test void cancelarCierraSolicitudSinMarcarActividadFisica() {var e=iniciar();var s=actual(e.id());ejecutar.cancelar(e.id(),"Prueba cancelada",TECNICO);assertEquals(SolicitudEvidencia.Estado.CANCELADA,actual(e.id()).estado());assertNull(actual(e.id()).proximoSeguimiento());assertFalse(ejecutar.listarPasos(e.id(),TECNICO).getFirst().ejecucionPaso().cumplido());assertThrows(ConflictoEjecucionException.class,()->automatizar.recibirSimulada(e.id(),s.id(),s.contexto().responsableEmail(),UUID.randomUUID().toString(),imagenes(),TECNICO));}
    @Test void propietarioAjenoNoPuedeLeerDecidirNiDescargar() {var e=iniciar();var r=recibir(e);var foto=automatizar.evidencias(e.id(),r.id(),TECNICO).getFirst();assertThrows(AccessDeniedException.class,()->automatizar.listar(e.id(),OTRO));assertThrows(AccessDeniedException.class,()->automatizar.decidir(e.id(),r.id(),r.version(),true,false,null,OTRO));assertThrows(AccessDeniedException.class,()->automatizar.descargar(e.id(),r.id(),foto.id(),OTRO));}
    @Test void dosDecisionesConcurrentesSoloCompletanUnaVez() throws Exception {var e=iniciar();var r=recibir(e);var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);Callable<Boolean> decidir=()->{inicio.await();try{automatizar.decidir(e.id(),r.id(),r.version(),true,false,null,TECNICO);return true;}catch(ConflictoEjecucionException x){return false;}};try{var a=pool.submit(decidir);var b=pool.submit(decidir);inicio.countDown();assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));assertEquals(1,automatizar.eventos(e.id(),TECNICO).stream().filter(v->v.tipo().equals("EVIDENCIA_APROBADA")).count());}finally{pool.shutdownNow();}}
    @Test void contextoDeInicioNoSePuedeReutilizarConOtraSucursal() {String clave=UUID.randomUUID().toString();ejecutar.iniciar(procedimientoId,sucursalId,clave,TECNICO);var otra=directorio.crear("QA_02","Otra sintética","QA","qa-no-enviar@dimarsa.cl");assertThrows(ConflictoEjecucionException.class,()->ejecutar.iniciar(procedimientoId,otra.id(),clave,TECNICO));assertEquals(1,contar("se_ejecucion"));}
    @Test void fallaPosteriorRevierteEnvioLogicoPasoYSolicitud() {assertThrows(IllegalStateException.class,()->new TransactionTemplate(tm).executeWithoutResult(t->{iniciar();throw new IllegalStateException("Fallo sintético posterior");}));assertEquals(0,contar("se_solicitud_evidencia"));assertEquals(0,contar("se_ejecucion"));assertEquals(0,contar("se_evento_ejecucion"));}
    @Test void falloSimuladoDelProveedorRevierteSolicitudYPermiteReintentoSinDuplicar() {
        doThrow(new IllegalStateException("Proveedor de prueba no disponible")).when(correo).enviar(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(0));
        String clave=UUID.randomUUID().toString();assertThrows(IllegalStateException.class,()->ejecutar.iniciar(procedimientoId,sucursalId,clave,TECNICO));
        assertEquals(0,contar("se_ejecucion"));assertEquals(0,contar("se_solicitud_evidencia"));
        doCallRealMethod().when(correo).enviar(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt());
        ejecutar.iniciar(procedimientoId,sucursalId,clave,TECNICO);assertEquals(1,contar("se_ejecucion"));assertEquals(1,contar("se_solicitud_evidencia"));
    }
    @Test void avisosSoloSeMuestranAlResponsableInternoYDesaparecenTrasDecision() {
        var e=iniciar();assertTrue(automatizar.avisos(TECNICO).isEmpty());var r=recibir(e);
        assertEquals(1,automatizar.avisos(TECNICO).size());assertTrue(automatizar.avisos(OTRO).isEmpty());
        automatizar.decidir(e.id(),r.id(),r.version(),true,false,null,TECNICO);assertTrue(automatizar.avisos(TECNICO).isEmpty());
    }
    int seisPasos(boolean esperarConfirmacion){
        int id=new TransactionTemplate(tm).execute(t->{var p=procedimientos.guardar(new Procedimiento(null,"Preparar POS · seis pasos",null,EstadoProcedimiento.BORRADOR,usuario(1),LocalDateTime.now(),null,null));
            for(int i=1;i<=6;i++)pasos.guardar(new Paso(null,p.id(),i,List.of("Verificar instalaciones","Bootear Debian 12","Comandos en terminal","Instalar Comercial","Instalar Transbank","Solicitar folios").get(i-1),i==1 || i==5));return p.id();});
        for(var p:pasos.buscarPorProcedimientoOrdenados(id)){
            var modo=p.orden()==1?ModoEjecucionPaso.SOLICITAR_EVIDENCIA:p.orden()==6?ModoEjecucionPaso.ENVIAR_CORREO:ModoEjecucionPaso.MANUAL_GUIADO;
            ConfiguracionPaso config=null;
            if(p.orden()==6)config=new ConfiguracionPaso(List.of(new cl.casol.backend.shared.domain.DestinatarioPaso("qa-control-no-enviar@dimarsa.cl",cl.casol.backend.shared.domain.DestinatarioPaso.Tipo.PRINCIPAL),new cl.casol.backend.shared.domain.DestinatarioPaso("qa-copia-no-enviar@dimarsa.cl",cl.casol.backend.shared.domain.DestinatarioPaso.Tipo.COPIA)),false,"Solicitar folios; no acreditar su carga",List.of(),esperarConfirmacion,List.of(24,72),120,esperarConfirmacion?ConfiguracionPaso.Condicion.RESPUESTA_VALIDADA:ConfiguracionPaso.Condicion.ENVIO_REGISTRADO,null);
            configurar.configurar(id,p.id(),modo,ConfiguracionPaso.resolver(config,modo,p.esCritico(),p.instruccion()),p.revisionConfiguracion(),"qa-admin@example.invalid");
        }jdbc.update("update pr_procedimiento set procedimiento_estado='PUBLICADO' where procedimiento_id=?",id);return id;
    }
    Ejecucion hastaFolios(boolean esperar){int id=seisPasos(esperar);var e=ejecutar.iniciar(id,sucursalId,UUID.randomUUID().toString(),TECNICO);var s=automatizar.listar(e.id(),TECNICO).getFirst();var r=automatizar.recibirSimulada(e.id(),s.id(),s.contexto().responsableEmail(),UUID.randomUUID().toString(),imagenes(),TECNICO);automatizar.decidir(e.id(),s.id(),r.version(),true,false,null,TECNICO);
        for(int orden=2;orden<=5;orden++){int actual=orden;var p=ejecutar.listarPasos(e.id(),TECNICO).stream().filter(v->v.orden()==actual).findFirst().orElseThrow().ejecucionPaso();ejecutar.siguiente(e.id(),p.id(),p.version(),true,TECNICO);}return e;}
    @Test void prepararPosCompletoConExactamenteSeisPasosSinCasillas(){var e=hastaFolios(false);var lista=ejecutar.listarPasos(e.id(),TECNICO);assertEquals(6,lista.size());assertTrue(lista.stream().allMatch(p->p.ejecucionPaso().cumplido()));assertEquals(6,pasos.buscarPorProcedimientoOrdenados(e.procedimientoId()).size());var mail=automatizar.listar(e.id(),TECNICO).getLast();assertEquals(SolicitudEvidencia.Estado.ENVIADA,mail.estado());assertEquals(2,mail.contexto().destinatarios().size());assertTrue(mail.politica().mensaje().contains("no acreditar"));assertEquals(EstadoEjecucion.COMPLETADA,ejecutar.completar(e.id(),TECNICO).estado());}
    @Test void foliosConConfirmacionNoSeCompletanSoloPorEnviarCorreo(){var e=hastaFolios(true);var mail=automatizar.listar(e.id(),TECNICO).getLast();assertEquals(SolicitudEvidencia.Estado.ESPERANDO_CONFIRMACION,mail.estado());assertFalse(ejecutar.listarPasos(e.id(),TECNICO).getLast().ejecucionPaso().cumplido());var r=automatizar.confirmarRespuesta(e.id(),mail.id(),mail.contexto().responsableEmail(),UUID.randomUUID().toString(),"Confirmación de prueba del responsable",TECNICO);assertFalse(ejecutar.listarPasos(e.id(),TECNICO).getLast().ejecucionPaso().cumplido());automatizar.decidir(e.id(),mail.id(),r.version(),true,false,null,TECNICO);assertTrue(ejecutar.listarPasos(e.id(),TECNICO).getLast().ejecucionPaso().cumplido());}
    @Test void pasoCriticoGuiadoExigeConfirmacionExplicita(){int id=seisPasos(false);var e=ejecutar.iniciar(id,sucursalId,UUID.randomUUID().toString(),TECNICO);var s=automatizar.listar(e.id(),TECNICO).getFirst();var r=automatizar.recibirSimulada(e.id(),s.id(),s.contexto().responsableEmail(),UUID.randomUUID().toString(),imagenes(),TECNICO);automatizar.decidir(e.id(),s.id(),r.version(),true,false,null,TECNICO);for(int i=2;i<=4;i++){var p=ejecutar.listarPasos(e.id(),TECNICO).get(i-1).ejecucionPaso();ejecutar.siguiente(e.id(),p.id(),p.version(),true,TECNICO);}var critico=ejecutar.listarPasos(e.id(),TECNICO).get(4).ejecucionPaso();assertThrows(ReglaEjecucionException.class,()->ejecutar.siguiente(e.id(),critico.id(),critico.version(),false,TECNICO));assertFalse(ejecutar.listarPasos(e.id(),TECNICO).get(4).ejecucionPaso().cumplido());}
    @Test void publicadoNoSeConfiguraSinPrepararRevision(){var p=pasos.buscarPorProcedimientoOrdenados(procedimientoId).getFirst();assertThrows(AccessDeniedException.class,()->configurar.configurar(procedimientoId,p.id(),ModoEjecucionPaso.SOLICITAR_EVIDENCIA,p.configuracion(),p.revisionConfiguracion(),TECNICO));assertThrows(cl.casol.backend.procedimiento.domain.exception.EstadoProcedimientoInvalidoException.class,()->configurar.configurar(procedimientoId,p.id(),ModoEjecucionPaso.SOLICITAR_EVIDENCIA,p.configuracion(),p.revisionConfiguracion(),"qa-admin@example.invalid"));}
    @Test void configuracionDeBorradorExigeAdminYControlDeRevision(){jdbc.update("update pr_procedimiento set procedimiento_estado='BORRADOR' where procedimiento_id=?",procedimientoId);var p=pasos.buscarPorProcedimientoOrdenados(procedimientoId).getFirst();assertThrows(AccessDeniedException.class,()->configurar.configurar(procedimientoId,p.id(),ModoEjecucionPaso.SOLICITAR_EVIDENCIA,p.configuracion(),p.revisionConfiguracion(),TECNICO));configurar.configurar(procedimientoId,p.id(),ModoEjecucionPaso.SOLICITAR_EVIDENCIA,p.configuracion(),p.revisionConfiguracion(),"qa-admin@example.invalid");assertThrows(ConflictoEjecucionException.class,()->configurar.configurar(procedimientoId,p.id(),ModoEjecucionPaso.MANUAL_GUIADO,p.configuracion(),p.revisionConfiguracion(),"qa-admin@example.invalid"));assertNotNull(jdbc.queryForObject("select paso_config_historial from pr_paso where paso_id=?",String.class,p.id()));}

    @Test void destinatariosPrincipalesYCopiaSeDeduplicanYCapturan(){var s=directorio.buscar(sucursalId);directorio.destinatarios(s.id(),List.of(new cl.casol.backend.shared.domain.DestinatarioPaso("QA-SUBJEFE@dimarsa.cl",cl.casol.backend.shared.domain.DestinatarioPaso.Tipo.COPIA),new cl.casol.backend.shared.domain.DestinatarioPaso("qa-subjefe@dimarsa.cl",cl.casol.backend.shared.domain.DestinatarioPaso.Tipo.PRINCIPAL)),s.version());var e=iniciar();assertEquals(2,actual(e.id()).contexto().destinatarios().size());assertEquals(cl.casol.backend.shared.domain.DestinatarioPaso.Tipo.PRINCIPAL,actual(e.id()).contexto().destinatarios().stream().filter(d->d.correo().equals("qa-subjefe@dimarsa.cl")).findFirst().orElseThrow().tipo());}
    @Test void tecnicoNoAgregaUnDestinatarioNoAutorizado(){assertThrows(AccessDeniedException.class,()->ejecutar.iniciar(procedimientoId,sucursalId,UUID.randomUUID().toString(),List.of(new cl.casol.backend.shared.domain.DestinatarioPaso("qa-no-autorizado@dimarsa.cl",cl.casol.backend.shared.domain.DestinatarioPaso.Tipo.COPIA)),TECNICO));assertEquals(0,contar("se_ejecucion"));}
    SolicitudEvidencia enviarDesdePaso(Ejecucion e){var p=ejecutar.listarPasos(e.id(),TECNICO).getFirst().ejecucionPaso();return automatizar.enviarPaso(e.id(),p.id(),sucursalId,directorio.buscar(sucursalId).contactos(),null,null,TECNICO);}
    @Test void envioExplicitoCapturaFechaMensajeDestinatariosEHistorial(){var e=ejecutar.iniciar(procedimientoId,TECNICO);var s=enviarDesdePaso(e);assertEquals(Instant.parse("2026-11-07T15:00:00Z"),s.fechaEnvio());assertTrue(s.mensajeEnviadoId().startsWith("SIMULADO-"));assertEquals(SolicitudEvidencia.Estado.ESPERANDO_EVIDENCIA,s.estado());assertNotNull(s.politica().configuracion().asunto());assertEquals(2,automatizar.historial(e.id(),TECNICO).size());assertFalse(ejecutar.listarPasos(e.id(),TECNICO).getFirst().ejecucionPaso().cumplido());}
    @Test void repetirEnvioNoDuplicaComunicacion(){var e=ejecutar.iniciar(procedimientoId,TECNICO);var s=enviarDesdePaso(e);assertEquals(s.id(),enviarDesdePaso(e).id());assertEquals(1,contar("se_solicitud_evidencia"));assertEquals(1,automatizar.eventos(e.id(),TECNICO).stream().filter(v->v.tipo().equals("SOLICITUD_ENVIADA_SIMULADA")).count());}
    @Test void falloDelProveedorNoDejaSolicitudFalsamenteEnviada(){var e=ejecutar.iniciar(procedimientoId,TECNICO);doThrow(new ReglaEjecucionException("Proveedor no disponible")).when(correo).enviar(any(),anyInt());assertThrows(ReglaEjecucionException.class,()->enviarDesdePaso(e));assertEquals(0,contar("se_solicitud_evidencia"));assertEquals(1,contar("se_ejecucion"));doCallRealMethod().when(correo).enviar(any(),anyInt());assertNotNull(enviarDesdePaso(e).fechaEnvio());}
    @Test void tecnicoNoPuedeEditarMensajeNiEnviarACorreoAjeno(){var e=ejecutar.iniciar(procedimientoId,TECNICO);var p=ejecutar.listarPasos(e.id(),TECNICO).getFirst().ejecucionPaso();assertThrows(AccessDeniedException.class,()->automatizar.enviarPaso(e.id(),p.id(),sucursalId,directorio.buscar(sucursalId).contactos(),"Texto ajeno","Otro texto",TECNICO));assertThrows(AccessDeniedException.class,()->automatizar.enviarPaso(e.id(),p.id(),sucursalId,List.of(new cl.casol.backend.shared.domain.DestinatarioPaso("qa-ajeno@dimarsa.cl",null)),null,null,TECNICO));assertEquals(0,contar("se_solicitud_evidencia"));}
    @Test void directorioGuardaCiudadYCargoSinPerderlosAlEditar(){var s=directorio.buscar(sucursalId);var u=directorio.ubicacion(s.id(),"Calbuco","Jefe de tienda",s.version());var editada=directorio.editar(u.id(),u.codigo(),u.nombre(),u.responsableNombre(),u.responsableEmail(),u.version());assertEquals("Calbuco",editada.ciudad());assertEquals("Jefe de tienda",directorio.estado(editada.id(),false,editada.version()).responsableCargo());}
    @Test void prepararPosDiferidoMantieneSeisPasosYEnvioEnCadaPaso(){var e=ejecutar.iniciar(seisPasos(false),TECNICO);assertEquals(0,contar("se_solicitud_evidencia"));var s=enviarDesdePaso(e);var r=automatizar.recibirSimulada(e.id(),s.id(),s.contexto().responsableEmail(),UUID.randomUUID().toString(),imagenes(),TECNICO);automatizar.decidir(e.id(),s.id(),r.version(),true,false,null,TECNICO);for(int i=1;i<=4;i++){var p=ejecutar.listarPasos(e.id(),TECNICO).get(i).ejecucionPaso();ejecutar.siguiente(e.id(),p.id(),p.version(),true,TECNICO);}assertEquals(1,contar("se_solicitud_evidencia"));var p=ejecutar.listarPasos(e.id(),TECNICO).getLast().ejecucionPaso();var cfg=p.contrato().configuracion();var mail=automatizar.enviarPaso(e.id(),p.id(),sucursalId,cfg.destinatarios(),null,null,TECNICO);assertEquals(SolicitudEvidencia.Estado.ENVIADA,mail.estado());assertEquals(6,ejecutar.listarPasos(e.id(),TECNICO).size());assertEquals(EstadoEjecucion.COMPLETADA,ejecutar.completar(e.id(),TECNICO).estado());}

    @Test void altaDeSucursalIncluyeCiudadYCargoEnLaMismaTransaccion(){var s=directorio.crearConUbicacion("QA_CIUDAD","Sucursal prueba","Responsable QA","qa@dimarsa.cl",List.of(),"Calbuco","Jefe de tienda");assertEquals("Calbuco",directorio.buscar(s.id()).ciudad());assertEquals("Jefe de tienda",directorio.buscar(s.id()).responsableCargo());}

    @Test void automaticoUsaElContextoPrevioSinInventarUnCorreo(){
        var p=pasos.buscarPorProcedimientoOrdenados(procedimientoId).getLast();
        jdbc.update("update pr_procedimiento set procedimiento_estado='BORRADOR' where procedimiento_id=?",procedimientoId);
        configurar.configurar(procedimientoId,p.id(),ModoEjecucionPaso.AUTOMATICO,ConfiguracionPaso.resolver(null,ModoEjecucionPaso.AUTOMATICO,true,null),p.revisionConfiguracion(),"qa-admin@example.invalid");
        jdbc.update("update pr_procedimiento set procedimiento_estado='PUBLICADO' where procedimiento_id=?",procedimientoId);
        var e=ejecutar.iniciar(procedimientoId,TECNICO);var s=enviarDesdePaso(e);
        var r=automatizar.recibirSimulada(e.id(),s.id(),s.contexto().responsableEmail(),UUID.randomUUID().toString(),imagenes(),TECNICO);
        automatizar.decidir(e.id(),s.id(),r.version(),true,false,null,TECNICO);
        var automatico=automatizar.listar(e.id(),TECNICO).stream().filter(v->!v.id().equals(s.id())).findFirst().orElseThrow();
        assertEquals(SolicitudEvidencia.Estado.ACCION_PENDIENTE_APROBACION,automatico.estado());assertNull(automatico.fechaEnvio());
        assertEquals(1,automatizar.eventos(e.id(),TECNICO).stream().filter(v->v.tipo().equals("SOLICITUD_ENVIADA_SIMULADA")).count());
    }

    @Test void consultarEstadoNoCreaSolicitudesNiConsultaElProveedorDeEnvio(){var e=ejecutar.iniciar(procedimientoId,TECNICO);doThrow(new ReglaEjecucionException("Proveedor de correo no disponible")).when(correo).enviar(any(),anyInt());for(int i=0;i<3;i++){assertTrue(automatizar.listar(e.id(),TECNICO).isEmpty());assertTrue(automatizar.historial(e.id(),TECNICO).isEmpty());assertEquals(2,ejecutar.listarPasos(e.id(),TECNICO).size());assertEquals(e.id(),ejecutar.buscar(e.id(),TECNICO).id());}verify(correo,never()).enviar(any(),anyInt());assertEquals(0,contar("se_solicitud_evidencia"));assertEquals(0,contar("se_evento_ejecucion"));}

    @Test void estadoCompletoUsaUnaTransaccionYNoCreaComunicaciones(){var e=ejecutar.iniciar(procedimientoId,TECNICO);var stats=emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();stats.setStatisticsEnabled(true);stats.clear();try{var estado=automatizar.consultarEstado(e.id(),TECNICO);assertTrue(estado.solicitudes().isEmpty());assertTrue(estado.comunicaciones().isEmpty());assertEquals(1,stats.getTransactionCount(),"Una respuesta no debe abrir una transacción por sección");verify(correo,never()).enviar(any(),anyInt());}finally{stats.setStatisticsEnabled(false);}assertEquals(0,contar("se_solicitud_evidencia"));}
    @Test void estadoEntregaMetadatosDeFotosSinDescargarSusBytes(){var e=iniciar();recibir(e);var estado=automatizar.consultarEstado(e.id(),TECNICO);assertEquals(3,estado.solicitudes().getFirst().fotos().size());assertTrue(estado.solicitudes().getFirst().fotos().stream().allMatch(f->f.id()!=null && f.sha256()!=null));}

    @Test void evidenciaSinSucursalConservaRecepcionYValidacionCritica(){
        var e=ejecutar.iniciar(procedimientoId,TECNICO);var p=ejecutar.listarPasos(e.id(),TECNICO).getFirst().ejecucionPaso();
        var destinatarios=List.of(new cl.casol.backend.shared.domain.DestinatarioPaso("qa-directo@dimarsa.cl",null),new cl.casol.backend.shared.domain.DestinatarioPaso("qa-copia@dimarsa.cl",cl.casol.backend.shared.domain.DestinatarioPaso.Tipo.COPIA));
        var s=automatizar.enviarPaso(e.id(),p.id(),null,destinatarios,"Fotos de instalaciones","Enviar las tres fotografías",TECNICO);
        assertNull(s.contexto().sucursalId());assertNull(s.contexto().responsableNombre());assertEquals(2,s.contexto().destinatarios().size());assertFalse(s.politica().configuracion().usarDestinatariosSucursal());
        assertEquals(SolicitudEvidencia.Estado.ESPERANDO_EVIDENCIA,s.estado());
        assertEquals(s.id(),automatizar.enviarPaso(e.id(),p.id(),null,destinatarios,"Fotos de instalaciones","Enviar las tres fotografías",TECNICO).id());
        var recibida=automatizar.recibirSimulada(e.id(),s.id(),"qa-directo@dimarsa.cl",UUID.randomUUID().toString(),imagenes(),TECNICO);
        assertEquals(3,contar("se_evidencia"));assertFalse(ejecutar.listarPasos(e.id(),TECNICO).getFirst().ejecucionPaso().cumplido());
        automatizar.decidir(e.id(),s.id(),recibida.version(),true,false,null,TECNICO);
        assertTrue(ejecutar.listarPasos(e.id(),TECNICO).getFirst().ejecucionPaso().cumplido());assertEquals(1,contar("se_solicitud_evidencia"));
    }
}
