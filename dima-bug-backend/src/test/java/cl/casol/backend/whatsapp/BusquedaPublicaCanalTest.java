package cl.casol.backend.whatsapp;

import cl.casol.backend.conocimiento.application.service.ConsultarChatbotService;
import cl.casol.backend.whatsapp.infrastructure.busqueda.BusquedaPublicaCanalAdapter;
import cl.casol.backend.whatsapp.application.service.ProcesarMensajeService;
import cl.casol.backend.whatsapp.infrastructure.simulado.WebhookSimuladoAdapter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import java.time.Clock;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static cl.casol.backend.whatsapp.domain.RespuestaCanal.Estado.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:whatsapp_qa;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never",
 "jwt.secret=clave-sintetica-solo-whatsapp-qa-12345678901234567890123456789","jwt.expiration=3600000",
 "dimabug.automatizacion.modo=SIMULADO","dimabug.automatizacion.intervalo-ms=3600000",
 "dimabug.auth.correo.habilitado=false","dimabug.automatizacion.seguimiento-habilitado=false"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class BusquedaPublicaCanalTest {
 @Autowired ConsultarChatbotService servicio;
 @Autowired JdbcTemplate jdbc;
 @Autowired org.springframework.context.ApplicationContext contexto;
 WebhookSimuladoAdapter entrada;
 @BeforeEach void datos(){
   assertTrue(jdbc.queryForObject("select database()",String.class).toLowerCase().contains("whatsapp_qa"));
   for(String t:List.of("co_solucion","co_conocimiento","u_usuario","u_rol"))jdbc.update("delete from "+t);
   jdbc.update("insert into u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) values(1,'ADMINISTRADOR',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
   jdbc.update("insert into u_usuario(usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) values(1,1,'QA','qa@example.invalid','SIN_LOGIN',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
   for(int i=1;i<=4;i++){
     jdbc.update("insert into co_conocimiento(conocimiento_id,conocimiento_titulo,conocimiento_descripcion,conocimiento_estado,creado_por,fecha_creacion) values(?,?,'internozx',?,1,CURRENT_TIMESTAMP)",i,"casozx"+i,i==2?"BORRADOR":"PUBLICADO");
     jdbc.update("insert into co_solucion(solucion_id,conocimiento_id,solucion_titulo,solucion_descripcion,solucion_tipo,solucion_orden,solucion_estado,solucion_autoservicio) values(?,?,?,?,'PASOS',1,?,?)",i,i,"Solución "+i,"instruccionzx"+i,i!=4,i!=3);
   }
   entrada=new WebhookSimuladoAdapter(new ProcesarMensajeService(new BusquedaPublicaCanalAdapter(servicio),m->List.of(),Clock.systemUTC()));
 }
 @Test void devuelveSoloCamposPublicos(){var r=entrada.recibir(WebhookSimuladoTest.evento("wamid.qa-a","qa-usuario-a","text","casozx1")).getFirst();assertEquals(RESULTADOS,r.estado());assertEquals(1,r.conocimientos().getFirst().id());assertEquals("Solución 1",r.conocimientos().getFirst().titulo());assertEquals("instruccionzx1",r.conocimientos().getFirst().instrucciones());assertFalse(r.mensaje().contains("casozx1"));}
 @Test void borrador(){assertEquals(SIN_RESULTADOS,entrada.recibir(WebhookSimuladoTest.evento("wamid.qa-a","qa-usuario-a","text","casozx2")).getFirst().estado());}
 @Test void restringida(){assertEquals(SIN_RESULTADOS,entrada.recibir(WebhookSimuladoTest.evento("wamid.qa-a","qa-usuario-a","text","casozx3")).getFirst().estado());}
 @Test void inactiva(){assertEquals(SIN_RESULTADOS,entrada.recibir(WebhookSimuladoTest.evento("wamid.qa-a","qa-usuario-a","text","casozx4")).getFirst().estado());}
 @Test void campoInterno(){assertEquals(SIN_RESULTADOS,entrada.recibir(WebhookSimuladoTest.evento("wamid.qa-a","qa-usuario-a","text","internozx")).getFirst().estado());}

 @Test void imagenRealConfirmadaBuscaEnH2SinExponerTituloInterno(){
   jdbc.update("update co_conocimiento set conocimiento_titulo='Error 0x0000011b' where conocimiento_id=1");
   var flujo=new WebhookSimuladoAdapter(new ProcesarMensajeService(new BusquedaPublicaCanalAdapter(servicio),
       new cl.casol.backend.whatsapp.infrastructure.ocr.OcrNodeProcesoAdapter(java.nio.file.Path.of("..")),Clock.systemUTC()));
   var propuesta=flujo.recibir(WebhookSimuladoTest.evento("wamid.qa-imagen","qa-usuario-a","image","qa-media-legible")).getFirst();
   assertEquals(CONFIRMACION,propuesta.estado());assertTrue(propuesta.conocimientos().isEmpty());
   var resultado=flujo.recibir(WebhookSimuladoTest.evento("wamid.qa-confirmacion","qa-usuario-a","text","CONFIRMAR "+propuesta.referencia())).getFirst();
   assertEquals(RESULTADOS,resultado.estado());assertEquals(1,resultado.conocimientos().getFirst().id());
   var json=new String(new cl.casol.backend.whatsapp.infrastructure.simulado.SalidaSimuladaAdapter().inspeccionar(resultado),java.nio.charset.StandardCharsets.UTF_8);
   assertFalse(json.contains("Error 0x0000011b"));assertFalse(json.contains("internozx"));assertTrue(json.contains("instruccionzx1"));
 }

 @Test void noSeRegistraSimulacionFueraDelPerfilQa(){
   assertTrue(contexto.getBeansOfType(WebhookSimuladoAdapter.class).isEmpty());
   assertTrue(contexto.getBeansOfType(cl.casol.backend.whatsapp.application.port.out.AlmacenWhatsAppPort.class).isEmpty());
   assertEquals(0,jdbc.queryForObject("select count(*) from information_schema.tables where lower(table_name) in ('wa_conversacion','wa_evento','wa_respuesta')",Integer.class));
 }
}
