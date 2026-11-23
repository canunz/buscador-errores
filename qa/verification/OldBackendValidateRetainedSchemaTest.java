package cl.casol.backend;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={
 "spring.datasource.url=${UNIVERSAL_ROLLBACK_DB_URL}","spring.datasource.username=root","spring.datasource.password=${UNIVERSAL_TEST_DB_PASSWORD}",
 "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver","spring.jpa.hibernate.ddl-auto=validate","spring.sql.init.mode=never",
 "jwt.secret=clave-sintetica-para-prueba-aislada-no-utilizable-en-staging-12345","jwt.expiration=3600000",
 "dimabug.auth.correo.habilitado=false","dimabug.automatizacion.modo=DESHABILITADO","dimabug.automatizacion.seguimiento-habilitado=false",
 "dimabug.gmail.envio-real-habilitado=false","dimabug.gmail.oauth-habilitado=false","dimabug.correo.bienvenida.habilitado=false","dimabug.correo.ticket-critico.habilitado=false"
})
class OldBackendValidateRetainedSchemaTest {
 @Autowired JdbcTemplate jdbc;
 @MockitoBean cl.casol.backend.correo.application.service.GmailOAuthService gmail;
 @MockitoBean cl.casol.backend.correo.application.port.out.GoogleGmailPort google;
 @Test void jarAnteriorValidaEsquemaConservandoColumnaYRestriccionesNuevas() {
  assertEquals(System.getenv("UNIVERSAL_TEST_SERVER_UUID"),jdbc.queryForObject("SELECT @@server_uuid",String.class));
  assertEquals("registro_universal_rollback",jdbc.queryForObject("SELECT DATABASE()",String.class));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='u_usuario' AND COLUMN_NAME='registro_estado'",Integer.class));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM u_usuario WHERE registro_estado<>'ADMINISTRADO' AND usuario_estado<>0",Integer.class));
 }
}
