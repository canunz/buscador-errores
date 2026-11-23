package cl.casol.backend;
import cl.casol.backend.whatsapp.infrastructure.busqueda.*;
import cl.casol.backend.whatsapp.domain.RespuestaCanal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=jdbc:h2:mem:catalogo_principal_http;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never",
 "jwt.secret=clave-sintetica-exclusiva-catalogo-principal-http-123456789","jwt.expiration=3600000",
 "dimabug.automatizacion.modo=SIMULADO","dimabug.automatizacion.intervalo-ms=3600000"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogoPrincipalHttpIntegrationTest {
 @LocalServerPort int puerto; @Autowired JdbcTemplate jdbc;
 @Test void resultadosPublicosYRevocacion(){
  assertTrue(jdbc.queryForObject("select database()",String.class).toLowerCase().contains("catalogo_principal_http"));
  jdbc.execute("CREATE TABLE IF NOT EXISTS co_conocimiento_busqueda (conocimiento_id INT PRIMARY KEY, contenido_busqueda TEXT NOT NULL)");
  jdbc.update("insert into u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) values(1,'ADMINISTRADOR',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
  jdbc.update("insert into u_usuario(usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) values(1,1,'QA','qa@example.invalid','SIN_LOGIN',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
  for(int id=1;id<=4;id++){
   jdbc.update("insert into co_conocimiento(conocimiento_id,conocimiento_titulo,conocimiento_descripcion,conocimiento_estado,creado_por,fecha_creacion) values(?,'Error conexión POS','Interna',?,1,CURRENT_TIMESTAMP)",id,id==2?"BORRADOR":"PUBLICADO");
   jdbc.update("insert into co_solucion(solucion_id,conocimiento_id,solucion_titulo,solucion_descripcion,solucion_tipo,solucion_orden,solucion_estado,solucion_autoservicio) values(?,?,'Reiniciar POS','Pasos autorizados','PASOS',1,?,?)",id,id,id!=3,id!=4);
  }
  var cliente=new CatalogoPublicoHttpClient("http://127.0.0.1:"+puerto,Duration.ofSeconds(2),Duration.ofSeconds(5));
  var encontradas=new BusquedaPublicaHttpAdapter(cliente).buscar("Error conexión POS");assertEquals(List.of(1),encontradas.stream().map(RespuestaCanal.Solucion::id).toList());
  var plan=new RespuestaCanal(RespuestaCanal.Estado.RESULTADOS,"Resultados",null,encontradas,List.of(),List.of());
  var preparar=new PrepararRespuestaPublicaHttpAdapter(cliente);assertEquals(1,preparar.preparar(plan).conocimientos().size());
  jdbc.update("update co_solucion set solucion_autoservicio=false where solucion_id=1");assertTrue(preparar.preparar(plan).conocimientos().isEmpty());
  jdbc.update("update co_solucion set solucion_autoservicio=true where solucion_id=1");
  jdbc.update("update co_conocimiento set conocimiento_estado='BORRADOR' where conocimiento_id=1");assertTrue(preparar.preparar(plan).conocimientos().isEmpty());
 }
}
