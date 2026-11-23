package cl.casol.backend;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.application.service.AutenticarUsuarioService;
import cl.casol.backend.identidad.infrastructure.security.UsuarioAutenticadoCache;
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
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties={
 "spring.datasource.url=${UNIVERSAL_ROLLBACK_DB_URL}","spring.datasource.username=root","spring.datasource.password=${UNIVERSAL_TEST_DB_PASSWORD}",
 "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver","spring.jpa.hibernate.ddl-auto=create","spring.sql.init.mode=never","spring.jpa.open-in-view=false",
 "jwt.secret=clave-sintetica-para-prueba-aislada-no-utilizable-en-staging-12345","jwt.expiration=3600000",
 "dimabug.auth.correo.habilitado=false","dimabug.automatizacion.modo=DESHABILITADO","dimabug.automatizacion.seguimiento-habilitado=false",
 "dimabug.gmail.envio-real-habilitado=false","dimabug.gmail.oauth-habilitado=false","dimabug.correo.bienvenida.habilitado=false","dimabug.correo.ticket-critico.habilitado=false"
})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class OldBackendRollbackGuardTest {
 @Autowired JdbcTemplate jdbc;
 @Autowired UsuarioRepository usuarios;
 @Autowired TokenServicePort tokens;
 @Autowired AutenticarUsuarioService auth;
 @Autowired UsuarioAutenticadoCache cache;
 @Autowired PlatformTransactionManager tm;
 @Autowired MockMvc mvc;
 @MockitoBean EnvioCodigoAccesoPort envio;
 @MockitoBean GmailOAuthService gmail;
 @MockitoBean cl.casol.backend.correo.application.port.out.GoogleGmailPort google;

 @Test void cuarentenaYCachesFriasImpidenQueJarAnteriorAutoriceAutorregistros() throws Exception {
  assertEquals(System.getenv("UNIVERSAL_TEST_SERVER_UUID"),jdbc.queryForObject("SELECT @@server_uuid",String.class));
  assertEquals("registro_universal_rollback",jdbc.queryForObject("SELECT DATABASE()",String.class));
  String migration=Files.readString(Path.of(System.getenv("UNIVERSAL_MIGRATION_FILE")));
  for(String q:migration.replaceAll("(?m)^--.*$","").split(";"))if(!q.isBlank())jdbc.execute(q);
  jdbc.execute("ALTER TABLE u_usuario ADD COLUMN usuario_ultimo_ingreso DATETIME NULL");
  jdbc.update("INSERT INTO u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) VALUES(11,'USUARIO',true,NOW(),NOW()),(13,'ADMINISTRADOR',true,NOW(),NOW())");
  String[] estados={"ADMINISTRADO","AUTO_PENDIENTE","AUTO_CONFIRMADO","AUTO_CANCELADO"};
  String[] emails={"administrado@example.invalid","pendiente@example.invalid","confirmado@example.invalid","cancelado@example.invalid"};
  for(int i=0;i<4;i++)jdbc.update("INSERT INTO u_usuario(rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion,registro_estado) VALUES(11,'Sintético',?,'hash-intacto',?,NOW(),NOW(),?)",emails[i],i==0||i==2,estados[i]);
  Map<String,String> jwt=new HashMap<>();
  for(String e:emails)jwt.put(e,tokens.generarToken(usuarios.buscarPorEmail(e).orElseThrow()));
  // Calibrate the real OLD security chain: it does not recognize AUTO_CONFIRMADO.
  assertEquals(200,mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+jwt.get(emails[2]))).andReturn().getResponse().getStatus());
  assertEquals(200,mvc.perform(get("/api/procedimientos").header("Authorization","Bearer "+jwt.get(emails[2]))).andReturn().getResponse().getStatus());
  int uid=jdbc.queryForObject("SELECT usuario_id FROM u_usuario WHERE usuario_email=?",Integer.class,emails[2]);
  jdbc.update("INSERT INTO u_codigo_acceso(usuario_id,codigo_hash,creado_en,expira_en,intentos,usado) VALUES(?,'hash-sintetico-no-utilizable',NOW(),DATE_ADD(NOW(),INTERVAL 5 MINUTE),0,0)",uid);
  long cid=jdbc.queryForObject("SELECT MAX(codigo_id) FROM u_codigo_acceso",Long.class);
  jdbc.update("INSERT INTO u_correo_codigo(id,codigo_id,contenido_cifrado,estado,intentos,expira,proximo_intento,actualizado_en) VALUES('simulado',?,'cifrado-sintetico-no-descifrable','PENDIENTE',0,DATE_ADD(NOW(),INTERVAL 5 MINUTE),NOW(),NOW())",cid);
  String baseline=jdbc.queryForObject("SELECT SHA2(CONCAT_WS('|',usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion),256) FROM u_usuario WHERE registro_estado='ADMINISTRADO'",String.class);
  String plan=Files.readString(Path.of(System.getenv("UNIVERSAL_QUARANTINE_FILE")));
  new TransactionTemplate(tm).executeWithoutResult(tx->{
   for(String q:plan.replaceAll("(?m)^--.*$","").split(";")) {
    if(q.isBlank() || q.strip().equalsIgnoreCase("START TRANSACTION"))continue;
    if(q.strip().startsWith("SELECT usuario_id"))jdbc.queryForList(q);
    else if(q.strip().startsWith("SELECT COUNT"))assertEquals(0,jdbc.queryForObject(q,Integer.class));
    else jdbc.update(q);
   }
  });
  assertEquals(baseline,jdbc.queryForObject("SELECT SHA2(CONCAT_WS('|',usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion),256) FROM u_usuario WHERE registro_estado='ADMINISTRADO'",String.class));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM u_correo_codigo WHERE contenido_cifrado IS NOT NULL",Integer.class));
  // A real rollback MUST stop/restart Java; empty both repository/JWT caches as on cold boot.
  for(String e:emails)cache.invalidar(e);
  for(int i=1;i<4;i++) {
   assertEquals(401,mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+jwt.get(emails[i]))).andReturn().getResponse().getStatus());
   assertEquals(401,mvc.perform(get("/api/procedimientos").header("Authorization","Bearer "+jwt.get(emails[i]))).andReturn().getResponse().getStatus());
   auth.solicitarCodigo(emails[i]);
  }
  assertEquals(200,mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+jwt.get(emails[0]))).andReturn().getResponse().getStatus());
  verifyNoInteractions(envio,gmail,google);
 }
}
