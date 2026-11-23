package cl.casol.backend;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.correo.application.service.GmailOAuthService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.env.Environment;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={
 "spring.profiles.active=qa-skill45",
 "spring.datasource.url=${SKILL45_URL}","spring.datasource.username=qa_runtime",
 "spring.datasource.password=${SKILL45_PASSWORD}","spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
 "spring.datasource.hikari.data-source-properties.trustCertificateKeyStoreUrl=${SKILL45_STORE_URI}",
 "spring.datasource.hikari.data-source-properties.trustCertificateKeyStoreType=JKS",
 "spring.datasource.hikari.data-source-properties.trustCertificateKeyStorePassword=qa-public-ca-integrity",
 "spring.datasource.hikari.maximum-pool-size=3","spring.datasource.hikari.minimum-idle=1",
 "spring.jpa.hibernate.ddl-auto=validate","spring.sql.init.mode=never","spring.jpa.open-in-view=false",
 "jwt.secret=${SKILL45_JWT}","jwt.expiration=3600000",
 "dimabug.storage.tickets-dir=${SKILL45_STORAGE}","dimabug.storage.materiales-dir=${SKILL45_MATERIALS}",
 "dimabug.auth.correo.habilitado=false","dimabug.auth.registro-universal.habilitado=false",
 "dimabug.automatizacion.modo=DESHABILITADO","dimabug.automatizacion.seguimiento.habilitado=false",
 "dimabug.automatizacion.seguimiento-habilitado=false","dimabug.gmail.envio-real-habilitado=false",
 "dimabug.gmail.oauth-habilitado=false","dimabug.correo.bienvenida.habilitado=false",
 "dimabug.correo.ticket-critico.habilitado=false","spring.autoconfigure.exclude=org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration"
})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Skill45TicketMySqlTest {
 @Autowired JdbcTemplate jdbc;
 @Autowired MockMvc mvc;
 @Autowired TokenServicePort tokens;
 @Autowired UsuarioRepository usuarios;
 @Autowired Environment env;
 @Autowired EntityManagerFactory emf;
 @MockitoBean EnvioCodigoAccesoPort envio;
 @MockitoBean GmailOAuthService gmail;
 @MockitoBean cl.casol.backend.correo.application.port.out.GoogleGmailPort google;
 String token(int id){return tokens.generarToken(usuarios.buscarPorId(id).orElseThrow());}
 int count(String table){assertTrue(Set.of("ticket","ticket_adjunto","u_codigo_acceso","u_correo_codigo").contains(table));return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
 byte[] photo(String ext) throws Exception {try(var in=getClass().getResourceAsStream("/tickets/foto."+ext)){assertNotNull(in);return in.readAllBytes();}}
 Set<String> files() throws Exception {try(var stream=Files.list(Path.of(System.getenv("SKILL45_STORAGE")))){return stream.map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet());}}
 org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder request(String jwt) {
  return multipart("/api/tickets/solicitudes").param("titulo","Incidencia sintética QA").param("descripcion","Solo datos sintéticos, sin personas reales")
   .param("nombreCompleto","Persona declarada sintética").param("sucursalId","1").param("departamentoId","1")
   .param("contacto","+56 9 1234 5678").param("equipo","").param("codigoUsuario","").header("Authorization","Bearer "+jwt);
 }
 @BeforeEach void isolation() {
  assertEquals(System.getenv("SKILL45_SERVER_UUID"),jdbc.queryForObject("SELECT @@server_uuid",String.class));
  assertEquals(System.getenv("SKILL45_DATABASE"),jdbc.queryForObject("SELECT DATABASE()",String.class));
  assertTrue(jdbc.queryForObject("SELECT VERSION()",String.class).startsWith("8.0."));
 }
 @AfterEach void noMail() {verifyNoInteractions(envio,gmail,google);assertEquals(0,count("u_codigo_acceso"));assertEquals(0,count("u_correo_codigo"));}
 @Test @Order(1) void hibernateValidateRealYConexionTLSConCuentaDmlExclusiva() {
  assertTrue(emf.isOpen());assertEquals("validate",emf.getProperties().get("hibernate.hbm2ddl.auto"));
  assertEquals("validate",env.getProperty("spring.jpa.hibernate.ddl-auto"));
  assertEquals(3,count("ticket"));
  var cipher=jdbc.queryForList("SHOW SESSION STATUS LIKE 'Ssl_cipher'");assertEquals(1,cipher.size());
  assertFalse(String.valueOf(cipher.getFirst().get("Value")).isBlank());
  assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.execute("CREATE TABLE s45_ddl_forbidden(id INT)"));
 }
 @Test @Order(2) void legacyPropioSeConsultaYTicketAjenoEs404() throws Exception {
  String own=token(1),other=token(2);
  var r=mvc.perform(get("/api/tickets/10").header("Authorization","Bearer "+own)).andReturn().getResponse();assertEquals(200,r.getStatus());
  assertNull(com.jayway.jsonpath.JsonPath.read(r.getContentAsString(),"$.datosSolicitante"));
  assertEquals(404,mvc.perform(get("/api/tickets/11").header("Authorization","Bearer "+own)).andReturn().getResponse().getStatus());
  assertEquals(404,mvc.perform(get("/api/tickets/10").header("Authorization","Bearer "+other)).andReturn().getResponse().getStatus());
  var list=mvc.perform(get("/api/tickets").header("Authorization","Bearer "+own)).andReturn().getResponse();assertEquals(200,list.getStatus());
  List<Number> ids=com.jayway.jsonpath.JsonPath.read(list.getContentAsString(),"$.contenido[*].id");assertEquals(List.of(10),ids.stream().map(Number::intValue).toList());
 }
 @Test @Order(3) void fotosAnterioresYPrivacidadDeNotasInternasSeConservan() throws Exception {
  var own=mvc.perform(get("/api/tickets/10/adjuntos/1").header("Authorization","Bearer "+token(1))).andReturn().getResponse();assertEquals(200,own.getStatus());assertArrayEquals(photo("png"),own.getContentAsByteArray());
  assertEquals(404,mvc.perform(get("/api/tickets/10/adjuntos/1").header("Authorization","Bearer "+token(2))).andReturn().getResponse().getStatus());
  assertEquals(404,mvc.perform(get("/api/tickets/10/adjuntos/2").header("Authorization","Bearer "+token(1))).andReturn().getResponse().getStatus());
 }
 @Test @Order(4) void crearConFotoPrioridadServidorEIdentidadInmutableYConsultaPrivada() throws Exception {
  String own=token(1),other=token(2);
  var req=request(own).file(new MockMultipartFile("fotos","../../captura.png","image/png",photo("png")))
   .param("prioridad","CRITICA").param("solicitanteId","2").param("asignadoId","3")
   .param("sucursalNombre","No confiable").param("departamentoNombre","No confiable");
  var r=mvc.perform(req).andReturn().getResponse();assertEquals(201,r.getStatus());
  Number id=com.jayway.jsonpath.JsonPath.read(r.getContentAsString(),"$.id");
  assertEquals("MEDIA",jdbc.queryForObject("SELECT prioridad FROM ticket WHERE id=?",String.class,id));
  assertEquals(1,jdbc.queryForObject("SELECT solicitante_id FROM ticket WHERE id=?",Integer.class,id));
  assertNull(jdbc.queryForObject("SELECT asignado_id FROM ticket WHERE id=?",Integer.class,id));
  assertEquals("Sucursal sintética QA",com.jayway.jsonpath.JsonPath.read(r.getContentAsString(),"$.datosSolicitante.sucursalNombre"));
  assertEquals("Persona declarada sintética",jdbc.queryForObject("SELECT sol_nombre_completo FROM ticket WHERE id=?",String.class,id));
  assertEquals("Cuenta sintética 1",jdbc.queryForObject("SELECT usuario_nombre FROM u_usuario WHERE usuario_id=1",String.class));
  Number aid=com.jayway.jsonpath.JsonPath.read(r.getContentAsString(),"$.adjuntos[0].id");
  assertEquals("captura.png",com.jayway.jsonpath.JsonPath.read(r.getContentAsString(),"$.adjuntos[0].nombre"));
  var download=mvc.perform(get("/api/tickets/"+id+"/adjuntos/"+aid).header("Authorization","Bearer "+own)).andReturn().getResponse();
  assertEquals(200,download.getStatus());assertArrayEquals(photo("png"),download.getContentAsByteArray());assertEquals("private, no-store",download.getHeader("Cache-Control"));
  assertEquals(404,mvc.perform(get("/api/tickets/"+id).header("Authorization","Bearer "+other)).andReturn().getResponse().getStatus());
  assertEquals(404,mvc.perform(get("/api/tickets/"+id+"/adjuntos/"+aid).header("Authorization","Bearer "+other)).andReturn().getResponse().getStatus());
  assertEquals(200,mvc.perform(get("/api/tickets/"+id).header("Authorization","Bearer "+own)).andReturn().getResponse().getStatus());
  // Support uses the real MySQL JSON/JDBC optimized projection, rather than only JPA.
  var support=mvc.perform(get("/api/tickets/"+id).header("Authorization","Bearer "+token(3))).andReturn().getResponse();assertEquals(200,support.getStatus());
  assertEquals("Persona declarada sintética",com.jayway.jsonpath.JsonPath.read(support.getContentAsString(),"$.datosSolicitante.nombreCompleto"));
 }
 @Test @Order(5) void opcionesActivasSinDirectorioInterno() throws Exception {
  String own=token(1);
  var r=mvc.perform(get("/api/tickets/solicitudes/catalogos").header("Authorization","Bearer "+own)).andReturn().getResponse();assertEquals(200,r.getStatus());
  List<?> s=com.jayway.jsonpath.JsonPath.read(r.getContentAsString(),"$.sucursales");assertEquals(1,s.size());
  List<?> d=com.jayway.jsonpath.JsonPath.read(r.getContentAsString(),"$.departamentos");assertEquals(1,d.size());
  assertFalse(r.getContentAsString().contains("responsable"));assertFalse(r.getContentAsString().contains("email"));
  for(String path:List.of("/api/sucursales","/api/departamentos","/api/usuarios","/api/conocimientos","/api/procedimientos"))
   assertEquals(403,mvc.perform(get(path).header("Authorization","Bearer "+own)).andReturn().getResponse().getStatus());
 }
 @Test @Order(6) void camposOpcionalesPuedenSerNulosYSinFotografias() throws Exception {
  var r=mvc.perform(request(token(1))).andReturn().getResponse();assertEquals(201,r.getStatus());
  Number id=com.jayway.jsonpath.JsonPath.read(r.getContentAsString(),"$.id");
  assertNull(jdbc.queryForObject("SELECT sol_equipo FROM ticket WHERE id=?",String.class,id));
  assertNull(jdbc.queryForObject("SELECT sol_codigo_usuario FROM ticket WHERE id=?",String.class,id));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ticket_adjunto WHERE ticket_id=?",Integer.class,id));
 }
 @Test @Order(7) void variasFotosRealesJpgPngWebp() throws Exception {
  var req=request(token(1));for(String ext:List.of("png","jpg","webp"))req.file(new MockMultipartFile("fotos","foto."+ext,"image/"+(ext.equals("jpg")?"jpeg":ext),photo(ext)));
  var r=mvc.perform(req).andReturn().getResponse();assertEquals(201,r.getStatus());Number id=com.jayway.jsonpath.JsonPath.read(r.getContentAsString(),"$.id");
  assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM ticket_adjunto WHERE ticket_id=?",Integer.class,id));
  assertEquals(Set.of("image/png","image/jpeg","image/webp"),new HashSet<>(jdbc.queryForList("SELECT mime FROM ticket_adjunto WHERE ticket_id=?",String.class,id)));
 }
 @Test @Order(8) void archivoInvalidoNoCreaTicketNiArchivoHuerfano() throws Exception {
  int tickets=count("ticket"),attachments=count("ticket_adjunto");Set<String> files=files();
  for(byte[] bad:List.of(new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff},"%PDF-not-a-photo".getBytes(),new byte[5242881])) {
   var r=mvc.perform(request(token(1)).file(new MockMultipartFile("fotos","foto.jpg","image/jpeg",bad))).andReturn().getResponse();assertEquals(400,r.getStatus());
   assertEquals(tickets,count("ticket"));assertEquals(attachments,count("ticket_adjunto"));assertEquals(files,files());
  }
 }
 @Test @Order(9) void datosInvalidosYBypassAntiguoNoInsertan() throws Exception {
  int before=count("ticket");
  for(var entry:Map.of("equipo","999.1.1.1","contacto","no es teléfono","sucursalId","2","departamentoId","2").entrySet()) {
   var params=new org.springframework.util.LinkedMultiValueMap<String,String>();
   params.set("titulo","T");params.set("descripcion","D");params.set("nombreCompleto","Persona QA");params.set("sucursalId","1");params.set("departamentoId","1");params.set("contacto","1234");params.set(entry.getKey(),entry.getValue());
   var r=mvc.perform(multipart("/api/tickets/solicitudes").params(params).header("Authorization","Bearer "+token(1))).andReturn().getResponse();assertEquals(400,r.getStatus());
  }
  var old=mvc.perform(post("/api/tickets").header("Authorization","Bearer "+token(1)).contentType("application/json").content("{\"titulo\":\"T\",\"descripcion\":\"D\",\"prioridad\":\"CRITICA\"}")).andReturn().getResponse();assertEquals(400,old.getStatus());
  assertEquals(before,count("ticket"));
 }
 @Test @Order(10) void checkMysqlRechazaDatosParcialesYConservaLegadosNulos() {
  for(String set:List.of("sol_contacto='1234'","sol_nombre_completo='Solo nombre'","sol_sucursal_id=1","sol_equipo='192.168.1.1'")) {
   var rejection=assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE ticket SET "+set+" WHERE id=10"));
   Throwable cause=rejection;while(cause!=null && !(cause instanceof java.sql.SQLException))cause=cause.getCause();
   assertNotNull(cause);var sql=(java.sql.SQLException)cause;
   assertEquals(3819,sql.getErrorCode());assertEquals("HY000",sql.getSQLState());
   assertTrue(sql.getMessage().contains("ck_ticket_sol_datos"));
  }
  assertNull(jdbc.queryForObject("SELECT sol_nombre_completo FROM ticket WHERE id=10",String.class));
  assertNull(jdbc.queryForObject("SELECT sol_contacto FROM ticket WHERE id=10",String.class));
 }
}
