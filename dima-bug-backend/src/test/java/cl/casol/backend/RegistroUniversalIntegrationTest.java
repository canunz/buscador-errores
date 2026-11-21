package cl.casol.backend;

import cl.casol.backend.identidad.application.service.*;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.domain.exception.CodigoAccesoInvalidoException;
import cl.casol.backend.correo.application.service.GmailOAuthService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:universal;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never",
 "jwt.secret=clave-sintetica-exclusiva-de-pruebas-12345678901234567890","jwt.expiration=3600000",
 "dimabug.auth.registro-universal.habilitado=true","dimabug.auth.registro-universal.maximo-hora=3",
 "dimabug.auth.limite-ip-minuto=1000","dimabug.auth.correo.habilitado=false",
 "dimabug.automatizacion.modo=DESHABILITADO","dimabug.automatizacion.seguimiento.habilitado=false",
 "dimabug.gmail.envio-real-habilitado=false","dimabug.gmail.oauth-habilitado=false",
 "dimabug.correo.bienvenida.habilitado=false","dimabug.correo.ticket-critico.habilitado=false"
})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class RegistroUniversalIntegrationTest {
 @Autowired AutenticarUsuarioService auth;
 @Autowired AdministrarUsuariosService admin;
 @Autowired JdbcTemplate jdbc;
 @Autowired MockMvc mvc;
 @Autowired PlatformTransactionManager tm;
 @Autowired UsuarioRepository usuarios;
 @MockitoBean EnvioCodigoAccesoPort envio;
 @MockitoBean GmailOAuthService gmail;
 @MockitoBean cl.casol.backend.correo.application.port.out.GoogleGmailPort google;
 final Map<String,String> codigos=new ConcurrentHashMap<>();
 @BeforeEach void datos() {
  assertTrue(jdbc.queryForObject("SELECT H2VERSION()",String.class)!=null);
  jdbc.execute("ALTER TABLE u_usuario ADD COLUMN IF NOT EXISTS usuario_ultimo_ingreso TIMESTAMP NULL");
  // Existing MySQL ticket sequence query casts to UNSIGNED; H2 needs an equivalent type alias.
  jdbc.execute("CREATE DOMAIN IF NOT EXISTS UNSIGNED AS BIGINT");
  jdbc.update("DELETE FROM ticket_historial_estado");jdbc.update("DELETE FROM ticket");
  jdbc.update("DELETE FROM u_correo_codigo");jdbc.update("DELETE FROM u_codigo_acceso");
  jdbc.update("DELETE FROM u_usuario");jdbc.update("DELETE FROM u_rol");
  jdbc.update("INSERT INTO u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) VALUES (11,'USUARIO',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(12,'TECNICO',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
  jdbc.update("MERGE INTO u_sucursal(sucursal_id,sucursal_codigo,sucursal_nombre,responsable_nombre,responsable_email,sucursal_activa,revision) KEY(sucursal_id) VALUES(101,'QA-SOL','Sucursal QA','Responsable sintético','qa@example.invalid',true,0)");
  jdbc.update("MERGE INTO u_departamento(departamento_id,departamento_nombre,departamento_estado,departamento_version) KEY(departamento_id) VALUES(102,'Área QA',true,0)");
  codigos.clear();
  doAnswer(i->{codigos.put(i.getArgument(1),i.getArgument(3));return null;})
    .when(envio).encolar(any(),anyString(),anyString(),anyString(),anyInt());
 }
 int id(String email) {return jdbc.queryForObject("SELECT usuario_id FROM u_usuario WHERE usuario_email=?",Integer.class,email);}
 String estado(String e) {return jdbc.queryForObject("SELECT registro_estado FROM u_usuario WHERE usuario_email=?",String.class,e);}
 void legado(String e,boolean activo) {
  jdbc.update("INSERT INTO u_usuario(rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) VALUES(12,'Legado',?,'hash-legado',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",e,activo);
 }
 Usuario confirmar(String email) {auth.solicitarCodigo(email);assertTrue(codigos.containsKey(email));return auth.verificarCodigo(email,codigos.get(email));}
 String jwt(String email) throws Exception {
  auth.solicitarCodigo(email);
  var res=mvc.perform(post("/api/auth/codigo/verificar").contentType("application/json")
    .content("{\"email\":\""+email+"\",\"codigo\":\""+codigos.get(email)+"\"}"))
    .andReturn().getResponse();assertEquals(200,res.getStatus());
  return com.jayway.jsonpath.JsonPath.read(res.getContentAsString(),"$.token");
 }
 @Test void creaPendienteInactivoYConfirmaSoloConCodigo() {
  String e="nuevo@gmail.com";auth.solicitarCodigo(e);
  assertEquals("AUTO_PENDIENTE",estado(e));assertFalse(usuarios.buscarFresco(e).orElseThrow().puedeIngresar());
  assertThrows(CodigoAccesoInvalidoException.class,()->auth.verificarCodigo(e,"no-valido"));
  assertEquals("AUTO_PENDIENTE",estado(e));
  Usuario u=auth.verificarCodigo(e,codigos.get(e));assertTrue(u.puedeIngresar());assertTrue(u.isAccesoBasico());
  assertEquals("USUARIO",u.getRol().getNombre());assertEquals("AUTO_CONFIRMADO",estado(e));
  assertThrows(CodigoAccesoInvalidoException.class,()->auth.verificarCodigo(e,codigos.get(e)));
  verifyNoInteractions(gmail);
 }
 @Test void aceptaDominiosPersonalesCorporativosYOtrosSinPrivilegios() {
  for(String e:List.of("uno@dimarsa.cl","dos@hotmail.com","tres@otro.example")) {
   Usuario u=confirmar(e);assertEquals("USUARIO",u.getRol().getNombre());assertTrue(u.isAccesoBasico());
  }
 }
 @Test void bloqueadoAdministradoNoRecibeNiSeReactiva() {
  legado("bloqueado@outlook.com",false);auth.solicitarCodigo("bloqueado@outlook.com");
  assertFalse(codigos.containsKey("bloqueado@outlook.com"));assertEquals("ADMINISTRADO",estado("bloqueado@outlook.com"));
  assertThrows(CodigoAccesoInvalidoException.class,()->auth.verificarCodigo("bloqueado@outlook.com","123456"));
 }
 @Test void canceladoNoSeReactivaConCodigoAnterior() {
  String e="cancelar@gmail.com";auth.solicitarCodigo(e);
  admin.cambiarEstado(id(e),false,"admin@example.invalid");assertEquals("AUTO_CANCELADO",estado(e));
  assertThrows(CodigoAccesoInvalidoException.class,()->auth.verificarCodigo(e,codigos.get(e)));
  jdbc.update("MERGE INTO u_sucursal(sucursal_id,sucursal_codigo,sucursal_nombre,responsable_nombre,responsable_email,sucursal_activa,revision) KEY(sucursal_id) VALUES(101,'QA-SOL','Sucursal QA','Responsable sintético','qa@example.invalid',true,0)");
  jdbc.update("MERGE INTO u_departamento(departamento_id,departamento_nombre,departamento_estado,departamento_version) KEY(departamento_id) VALUES(102,'Área QA',true,0)");
  codigos.clear();auth.solicitarCodigo(e);assertTrue(codigos.isEmpty());
  assertThrows(IllegalArgumentException.class,()->admin.cambiarEstado(id(e),true,"admin@example.invalid"));
 }
 @Test void confirmadoBloqueadoNoSeReactivaYSuOrigenSeConserva() {
  String e="bloquear@gmail.com";confirmar(e);admin.cambiarEstado(id(e),false,"admin@example.invalid");
  jdbc.update("MERGE INTO u_sucursal(sucursal_id,sucursal_codigo,sucursal_nombre,responsable_nombre,responsable_email,sucursal_activa,revision) KEY(sucursal_id) VALUES(101,'QA-SOL','Sucursal QA','Responsable sintético','qa@example.invalid',true,0)");
  jdbc.update("MERGE INTO u_departamento(departamento_id,departamento_nombre,departamento_estado,departamento_version) KEY(departamento_id) VALUES(102,'Área QA',true,0)");
  codigos.clear();auth.solicitarCodigo(e);assertTrue(codigos.isEmpty());
  assertEquals("AUTO_CONFIRMADO",estado(e));assertFalse(usuarios.buscarFresco(e).orElseThrow().puedeIngresar());
 }
 @Test void rolYHashLegadosPermanecenIntactos() {
  String e="legado@dimarsa.cl";legado(e,true);Usuario u=confirmar(e);
  assertEquals("TECNICO",u.getRol().getNombre());assertFalse(u.isAccesoBasico());assertEquals("hash-legado",u.getPasswordHash());
  assertEquals("ADMINISTRADO",estado(e));
 }
 @Test void noPermitePromoverNiCambiarCorreoDeAutorregistro() {
  String e="basico@gmail.com";confirmar(e);
  assertThrows(IllegalArgumentException.class,()->admin.modificar(id(e),"Nombre",e,12,null));
  assertThrows(IllegalArgumentException.class,()->admin.modificar(id(e),"Nombre","otro@gmail.com",11,null));
 }
 @Test void expiracionYCincoIntentosSeConservan() {
  String e="intentos@gmail.com";auth.solicitarCodigo(e);
  for(int i=0;i<5;i++)assertThrows(CodigoAccesoInvalidoException.class,()->auth.verificarCodigo(e,"invalido"));
  assertThrows(CodigoAccesoInvalidoException.class,()->auth.verificarCodigo(e,codigos.get(e)));
  assertEquals("AUTO_PENDIENTE",estado(e));
  jdbc.update("UPDATE u_codigo_acceso SET intentos=0,expira_en=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP)");
  assertThrows(CodigoAccesoInvalidoException.class,()->auth.verificarCodigo(e,codigos.get(e)));
 }
 @Test void limiteGlobalPersisteYReenvioNoDuplica() {
  for(int i=0;i<4;i++)auth.solicitarCodigo("limite"+i+"@gmail.com");
  assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM u_usuario",Integer.class));
  auth.solicitarCodigo("limite0@gmail.com");
  assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM u_codigo_acceso",Integer.class));
 }
 @Test void rollbackNoDejaCuentaNiCodigo() {
  new TransactionTemplate(tm).executeWithoutResult(tx->{auth.solicitarCodigo("rollback@gmail.com");tx.setRollbackOnly();});
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM u_usuario",Integer.class));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM u_codigo_acceso",Integer.class));
 }
 @Test void solicitudConcurrenteCreaUnaCuentaYUnCodigo() throws Exception {
  try(var pool=Executors.newFixedThreadPool(4)) {
   var inicio=new CountDownLatch(1);List<Future<?>> fs=new ArrayList<>();
   for(int i=0;i<4;i++)fs.add(pool.submit(()->{inicio.await();auth.solicitarCodigo("CONCURRENTE@gmail.com");return null;}));
   inicio.countDown();for(var f:fs)f.get(30,TimeUnit.SECONDS);
  }
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM u_usuario",Integer.class));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM u_codigo_acceso",Integer.class));
 }
 @Test void verificacionConcurrenteConfirmaYConsumeUnaSolaVez() throws Exception {
  String e="consumo@gmail.com";auth.solicitarCodigo(e);
  try(var pool=Executors.newFixedThreadPool(2)) {
   var inicio=new CountDownLatch(1);Callable<Boolean> tarea=()->{inicio.await();try{auth.verificarCodigo(e,codigos.get(e));return true;}catch(CodigoAccesoInvalidoException ex){return false;}};
   var a=pool.submit(tarea);var b=pool.submit(tarea);inicio.countDown();assertNotEquals(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
  }
  assertEquals("AUTO_CONFIRMADO",estado(e));
 }
 @Test void jwtSoloPermiteTicketsPropiosYDeniegaRecursosInternos() throws Exception {
  String token=jwt("portal@gmail.com");
  assertEquals(200,mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus());
  for(String p:List.of("/api/conocimientos","/api/procedimientos","/api/sucursales","/api/hardware","/api/usuarios","/api/tickets/sugerencias?texto=incidencia","/api/publico/soluciones"))
   assertEquals(403,mvc.perform(get(p).header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus(),p);
  assertEquals(403,mvc.perform(post("/api/tickets/1/comentarios").header("Authorization","Bearer "+token).contentType("application/json").content("{\"texto\":\"mensaje\"}")).andReturn().getResponse().getStatus());
  assertEquals(403,mvc.perform(get("/api/tickets?texto=interno").header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus());
 }
 @Test void bloqueoRevocaAccesoSinEsperarCaducidadDeCacheOJwt() throws Exception {
  String e="revocar@gmail.com",token=jwt(e);
  assertEquals(200,mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus());
  admin.cambiarEstado(id(e),false,"admin@example.invalid");
  assertEquals(401,mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus());
 }
 @Test void crearConsultarPropioYResponder404EnTicketAjeno() throws Exception {
  String token=jwt("propietario@gmail.com");
  String nombreAntes=jdbc.queryForObject("SELECT usuario_nombre FROM u_usuario WHERE usuario_email='propietario@gmail.com'",String.class);
  assertEquals(400,mvc.perform(post("/api/tickets").header("Authorization","Bearer "+token).contentType("application/json")
    .content("{\"titulo\":\"Problema sintético\",\"descripcion\":\"Solo datos de prueba\"}"))
    .andReturn().getResponse().getStatus());
  var response=mvc.perform(multipart("/api/tickets/solicitudes").header("Authorization","Bearer "+token)
    .param("titulo","Problema sintético").param("descripcion","Solo datos de prueba")
    .param("nombreCompleto","Persona declarada QA").param("sucursalId","101").param("departamentoId","102")
    .param("contacto","anexo 1234").param("equipo","").param("codigoUsuario","")
    .param("solicitanteId","999").param("prioridad","CRITICA"))
    .andReturn().getResponse();assertEquals(201,response.getStatus());
  assertEquals(nombreAntes,jdbc.queryForObject("SELECT usuario_nombre FROM u_usuario WHERE usuario_email='propietario@gmail.com'",String.class));
  assertEquals("MEDIA",com.jayway.jsonpath.JsonPath.read(response.getContentAsString(),"$.prioridad"));
  assertEquals("Sucursal QA",com.jayway.jsonpath.JsonPath.read(response.getContentAsString(),"$.datosSolicitante.sucursalNombre"));
  Number tid=com.jayway.jsonpath.JsonPath.read(response.getContentAsString(),"$.id");
  assertEquals(id("propietario@gmail.com"),jdbc.queryForObject("SELECT solicitante_id FROM ticket WHERE id=?",Integer.class,tid));
  assertEquals("Persona declarada QA",jdbc.queryForObject("SELECT sol_nombre_completo FROM ticket WHERE id=?",String.class,tid));
  assertEquals(200,mvc.perform(get("/api/tickets/"+tid).header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus());
  String otro=jwt("ajeno@gmail.com");
  assertEquals(404,mvc.perform(get("/api/tickets/"+tid).header("Authorization","Bearer "+otro)).andReturn().getResponse().getStatus());
  var lista=mvc.perform(get("/api/tickets").header("Authorization","Bearer "+otro)).andReturn().getResponse();
  List<?> contenido=com.jayway.jsonpath.JsonPath.read(lista.getContentAsString(),"$.contenido");assertTrue(contenido.isEmpty());
 }
}
