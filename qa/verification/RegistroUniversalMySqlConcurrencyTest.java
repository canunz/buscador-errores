package cl.casol.backend;

import cl.casol.backend.identidad.application.service.*;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.domain.exception.CodigoAccesoInvalidoException;
import cl.casol.backend.identidad.infrastructure.persistence.adapter.*;
import cl.casol.backend.correo.application.service.GmailOAuthService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties={
 "spring.datasource.url=${UNIVERSAL_TEST_DB_URL}","spring.datasource.username=root",
 "spring.datasource.password=${UNIVERSAL_TEST_DB_PASSWORD}","spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never","spring.jpa.open-in-view=false",
 "jwt.secret=clave-sintetica-para-prueba-aislada-no-utilizable-en-staging-12345","jwt.expiration=3600000",
 "dimabug.auth.registro-universal.habilitado=true","dimabug.auth.registro-universal.maximo-hora=3",
 "dimabug.auth.registro-universal.maximo-dia=3","dimabug.auth.registro-universal.maximo-pendientes=3",
 "dimabug.auth.correo.habilitado=false","dimabug.automatizacion.modo=DESHABILITADO",
 "dimabug.automatizacion.seguimiento-habilitado=false","dimabug.gmail.envio-real-habilitado=false",
 "dimabug.gmail.oauth-habilitado=false","dimabug.correo.bienvenida.habilitado=false","dimabug.correo.ticket-critico.habilitado=false"
})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegistroUniversalMySqlConcurrencyTest {
 @Autowired AutenticarUsuarioService auth;
 @Autowired AdministrarUsuariosService admin;
 @Autowired CodigoAccesoRepository codigosRepo;
 @Autowired TokenServicePort tokens;
 @Autowired PasswordEncoderPort encoder;
 @Autowired JdbcTemplate jdbc;
 @Autowired MockMvc mvc;
 @MockitoSpyBean RegistroUniversalAdapter registro;
 @MockitoSpyBean UsuarioRepositoryAdapter usuarios;
 @MockitoBean EnvioCodigoAccesoPort envio;
 @MockitoBean GmailOAuthService gmail;
 @MockitoBean cl.casol.backend.correo.application.port.out.GoogleGmailPort google;
 final Map<String,String> codigos=new ConcurrentHashMap<>();

 @BeforeAll void aislamiento() throws Exception {
  try(var c=jdbc.getDataSource().getConnection()) {
   assertEquals("MySQL",c.getMetaData().getDatabaseProductName());
   assertTrue(c.getMetaData().getURL().startsWith("jdbc:mysql://127.0.0.1:"));
  }
  assertEquals(System.getenv("UNIVERSAL_TEST_SERVER_UUID"),jdbc.queryForObject("SELECT @@server_uuid",String.class));
  assertEquals("registro_universal_native",jdbc.queryForObject("SELECT DATABASE()",String.class));
  assertTrue(jdbc.queryForObject("SELECT VERSION()",String.class).startsWith("8.0."));
  jdbc.execute("ALTER TABLE u_usuario ADD COLUMN usuario_ultimo_ingreso DATETIME NULL");
  jdbc.execute("CREATE UNIQUE INDEX uq_usuario_email_normalizado ON u_usuario ((LOWER(TRIM(usuario_email))))");
  jdbc.execute("ALTER TABLE u_usuario ADD CONSTRAINT chk_usuario_registro_inactivo CHECK (registro_estado NOT IN ('AUTO_PENDIENTE','AUTO_CANCELADO') OR usuario_estado=0)");
 }
 @BeforeEach void datos() {
  assertEquals(System.getenv("UNIVERSAL_TEST_SERVER_UUID"),jdbc.queryForObject("SELECT @@server_uuid",String.class));
  jdbc.update("DELETE FROM u_correo_codigo");jdbc.update("DELETE FROM u_codigo_acceso");
  jdbc.update("DELETE FROM u_usuario");jdbc.update("DELETE FROM u_rol");
  jdbc.update("INSERT INTO u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) VALUES(11,'USUARIO',true,NOW(),NOW()),(13,'ADMINISTRADOR',true,NOW(),NOW())");
  jdbc.update("INSERT INTO u_usuario(rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) VALUES(13,'Administrador sintético','admin@example.invalid','hash-no-utilizable',true,NOW(),NOW())");
  codigos.clear();
  doAnswer(i->{codigos.put(i.getArgument(1),i.getArgument(3));return null;}).when(envio).encolar(any(),anyString(),anyString(),anyString(),anyInt());
 }
 @AfterEach void sinCorreos() {verifyNoInteractions(gmail,google);}
 int id(String e){return jdbc.queryForObject("SELECT usuario_id FROM u_usuario WHERE usuario_email=?",Integer.class,e);}
 int cantidad(String tabla){return jdbc.queryForObject("SELECT COUNT(*) FROM "+tabla,Integer.class);}
 void simultaneos(List<Callable<Boolean>> acciones) throws Exception {
  var inicio=new CyclicBarrier(acciones.size());
  try(var pool=Executors.newFixedThreadPool(acciones.size())) {
   List<Future<Boolean>> fs=new ArrayList<>();
   for(var accion:acciones)fs.add(pool.submit(()->{inicio.await(10,TimeUnit.SECONDS);return accion.call();}));
   for(var f:fs)assertTrue(f.get(35,TimeUnit.SECONDS));
  }
 }
 @Test @Order(1) void dosSolicitudesMismoCorreoSoloCreanUnaCuentaYUnCodigo() throws Exception {
  simultaneos(List.of(()->{auth.solicitarCodigo("MISMO@example.invalid");return true;},()->{auth.solicitarCodigo("mismo@example.invalid");return true;}));
  assertEquals(2,cantidad("u_usuario"));assertEquals(1,cantidad("u_codigo_acceso"));
  assertEquals("AUTO_PENDIENTE",jdbc.queryForObject("SELECT registro_estado FROM u_usuario WHERE usuario_id=?",String.class,id("mismo@example.invalid")));
  verify(envio,times(1)).encolar(any(),anyString(),anyString(),anyString(),anyInt());
 }
 @Test @Order(2) void seisAdmisionesConcurrentesNoSuperanCuotasPersistentes() throws Exception {
  List<Callable<Boolean>> tareas=new ArrayList<>();
  for(int i=0;i<6;i++){final int n=i;tareas.add(()->{auth.solicitarCodigo("cuota"+n+"@example.invalid");return true;});}
  simultaneos(tareas);
  assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM u_usuario WHERE registro_estado<>'ADMINISTRADO'",Integer.class));
  assertEquals(3,cantidad("u_codigo_acceso"));verify(envio,times(3)).encolar(any(),anyString(),anyString(),anyString(),anyInt());
 }
 @Test @Order(3) void dosReenviosConcurrentesRespetanCuotaPorCuenta() throws Exception {
  String e="reenvio@example.invalid";auth.solicitarCodigo(e);
  jdbc.update("UPDATE u_codigo_acceso SET creado_en=?",LocalDateTime.now().minusMinutes(2));
  for(int i=0;i<3;i++)jdbc.update("INSERT INTO u_codigo_acceso(usuario_id,codigo_hash,creado_en,expira_en,intentos,usado) VALUES(?,?,?,?,0,0)",id(e),"hash-sintetico-no-utilizable",LocalDateTime.now().minusMinutes(2),LocalDateTime.now().plusMinutes(3));
  clearInvocations(envio);
  simultaneos(List.of(()->{auth.solicitarCodigo(e);return true;},()->{auth.solicitarCodigo(e);return true;}));
  assertEquals(5,cantidad("u_codigo_acceso"));verify(envio,times(1)).encolar(any(),anyString(),anyString(),anyString(),anyInt());
 }
 @Test @Order(4) void consumoConcurrenteConfirmaUnaSolaVez() throws Exception {
  String e="consumo@example.invalid";auth.solicitarCodigo(e);
  try(var pool=Executors.newFixedThreadPool(2)) {
   var inicio=new CyclicBarrier(2);Callable<Boolean> f=()->{inicio.await();try{auth.verificarCodigo(e,codigos.get(e));return true;}catch(CodigoAccesoInvalidoException ex){return false;}};
   var a=pool.submit(f);var b=pool.submit(f);assertNotEquals(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS));
  }
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM u_usuario WHERE registro_estado='AUTO_CONFIRMADO' AND usuario_estado=1",Integer.class));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM u_codigo_acceso WHERE usado=1",Integer.class));
 }
 private void esperarBloqueoReal() throws Exception {
  long fin=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
  while(System.nanoTime()<fin){if(jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits",Integer.class)>0)return;Thread.sleep(25);}
  fail("NO_SE_OBSERVO_ESPERA_INNODB");
 }
 private void comprobarBloqueado(String e) throws Exception {
  var u=usuarios.buscarFresco(e).orElseThrow();assertFalse(u.isActivo());assertFalse(u.puedeIngresar());
  String token=tokens.generarToken(u); // A valid signature never overrides the current blocked DB state.
  assertEquals(401,mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus());
  assertThrows(CodigoAccesoInvalidoException.class,()->auth.verificarCodigo(e,codigos.get(e)));
 }
 @Test @Order(5) void bloqueoPrimeroImpideConfirmacionConcurrente() throws Exception {
  String e="bloquear-primero@example.invalid";auth.solicitarCodigo(e);
  var tomado=new CountDownLatch(1);var liberar=new CountDownLatch(1);
  doAnswer(i->{var u=i.callRealMethod();tomado.countDown();if(!liberar.await(15,TimeUnit.SECONDS))throw new IllegalStateException("TEST_TIMEOUT");return u;}).when(usuarios).buscarPorIdBloqueado(id(e));
  try(var pool=Executors.newFixedThreadPool(2)) {
   var bloqueo=pool.submit(()->admin.cambiarEstado(id(e),false,"admin@example.invalid"));
   assertTrue(tomado.await(10,TimeUnit.SECONDS));
   var confirmar=pool.submit(()->{try{auth.verificarCodigo(e,codigos.get(e));return true;}catch(CodigoAccesoInvalidoException ex){return false;}});
   try{esperarBloqueoReal();}finally{liberar.countDown();}
   assertFalse(bloqueo.get(25,TimeUnit.SECONDS).isActivo());assertFalse(confirmar.get(25,TimeUnit.SECONDS));
  }finally{liberar.countDown();}
  assertEquals("AUTO_CANCELADO",jdbc.queryForObject("SELECT registro_estado FROM u_usuario WHERE usuario_id=?",String.class,id(e)));comprobarBloqueado(e);
 }
 @Test @Order(6) void confirmacionPrimeroNoReviveCuentaTrasBloqueoConcurrente() throws Exception {
  String e="confirmar-primero@example.invalid";auth.solicitarCodigo(e);
  var tomado=new CountDownLatch(1);var liberar=new CountDownLatch(1);
  RegistroUniversalAdapter target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(registro);
  doAnswer(i->{var u=i.callRealMethod();tomado.countDown();if(!liberar.await(15,TimeUnit.SECONDS))throw new IllegalStateException("TEST_TIMEOUT");return u;}).when(target).resolver(eq(e),eq(false),isNull());
  try(var pool=Executors.newFixedThreadPool(2)) {
   var confirmar=pool.submit(()->auth.verificarCodigo(e,codigos.get(e)));
   assertTrue(tomado.await(10,TimeUnit.SECONDS));
   var bloqueo=pool.submit(()->admin.cambiarEstado(id(e),false,"admin@example.invalid"));
   try{esperarBloqueoReal();}finally{liberar.countDown();}
   assertTrue(confirmar.get(25,TimeUnit.SECONDS).isActivo());assertFalse(bloqueo.get(25,TimeUnit.SECONDS).isActivo());
  }finally{liberar.countDown();}
  comprobarBloqueado(e);
 }
}
