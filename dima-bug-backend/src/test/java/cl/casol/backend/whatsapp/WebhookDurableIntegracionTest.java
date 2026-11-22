package cl.casol.backend.whatsapp;

import cl.casol.backend.whatsapp.application.port.out.PrepararRespuestaPublicaPort;
import cl.casol.backend.whatsapp.domain.RespuestaCanal;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP + trabajadores reales + OCR Node local + buscador público + JPA H2 aislado. */
@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:wa_integracion;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never",
 "jwt.secret=clave-sintetica-solo-whatsapp-qa-12345678901234567890123456789","jwt.expiration=3600000",
 "dimabug.automatizacion.modo=SIMULADO","dimabug.automatizacion.intervalo-ms=3600000",
 "dimabug.auth.correo.habilitado=false","dimabug.automatizacion.seguimiento-habilitado=false",
 "dimabug.whatsapp.http-local.verify-token=token-sintetico-webhook-local-2026",
 "dimabug.whatsapp.http-local.app-secret=secreto-sintetico-webhook-local-2026",
 "dimabug.whatsapp.qa.raiz=..","dimabug.whatsapp.durable.sondeo-ms=100",
 "dimabug.whatsapp.durable.clave-cifrado=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
 "dimabug.whatsapp.durable.clave-hmac=MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI="})
@ActiveProfiles({"qa-whatsapp-http-local","qa-whatsapp-durable"})
@AutoConfigureMockMvc
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class WebhookDurableIntegracionTest {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 @MockitoSpyBean PrepararRespuestaPublicaPort preparador;
 byte[] evento(String id,String tipo,String contenido){return new String(WebhookSimuladoTest.evento(id,"qa-usuario-integral",tipo,contenido),StandardCharsets.UTF_8)
     .replace("1794560400",Long.toString(Instant.now().getEpochSecond())).getBytes(StandardCharsets.UTF_8);}
 void enviar(byte[] cuerpo)throws Exception{
  mvc.perform(post("/webhooks/whatsapp").contentType("application/json").header("X-Hub-Signature-256",WebhookHttpLocalTest.firma(cuerpo)).content(cuerpo))
    .andExpect(status().isOk()).andExpect(jsonPath("$.respuestas").isEmpty()).andExpect(jsonPath("$.aceptados").value(1));
 }
 void esperar(String sql,int esperado)throws Exception{
  long fin=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(45);
  while(System.nanoTime()<fin){if(jdbc.queryForObject(sql,Integer.class)==esperado)return;Thread.sleep(100);}
  fail("No se completó el flujo durable dentro del plazo");
 }
 @Test void imagenConfirmadaBuscaYPreparaSoloCamposPublicos()throws Exception{
  jdbc.update("insert into u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) values(1,'ADMINISTRADOR',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
  jdbc.update("insert into u_usuario(usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) values(1,1,'QA','qa@example.invalid','SIN_LOGIN',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
  for(int i=1;i<=4;i++){
   jdbc.update("insert into co_conocimiento(conocimiento_id,conocimiento_titulo,conocimiento_descripcion,conocimiento_estado,creado_por,fecha_creacion) values(?,'Error 0x0000011b','INTERNO_NO_PUBLICAR',?,1,CURRENT_TIMESTAMP)",i,i==2?"BORRADOR":"PUBLICADO");
   jdbc.update("insert into co_solucion(solucion_id,conocimiento_id,solucion_titulo,solucion_descripcion,solucion_tipo,solucion_orden,solucion_estado,solucion_autoservicio) values(?,?,?,?,'PASOS',1,?,?)",i,i,"Solución pública "+i,"Paso autorizado "+i,i!=4,i!=3);
  }
  byte[] imagen=evento("wamid.qa-integral-imagen","image","qa-media-legible");enviar(imagen);
  esperar("select count(*) from wa_respuesta where estado='SIMULADA'",1);
  String referencia=jdbc.queryForObject("select referencia from wa_conversacion",String.class);assertNotNull(referencia);
  assertEquals("CONFIRMACION",jdbc.queryForObject("select resultado_codigo from wa_evento",String.class));
  enviar(evento("wamid.qa-integral-confirmar","text","CONFIRMAR "+referencia));
  esperar("select count(*) from wa_respuesta where estado='SIMULADA'",2);
  assertEquals(1,jdbc.queryForObject("select count(*) from wa_evento where resultado_codigo='RESULTADOS'",Integer.class));
  var captor=ArgumentCaptor.forClass(RespuestaCanal.class);verify(preparador,atLeast(2)).preparar(captor.capture());
  var resultado=captor.getAllValues().stream().filter(r->r.estado()==RespuestaCanal.Estado.RESULTADOS).findFirst().orElseThrow();
  assertEquals(1,resultado.conocimientos().size());assertEquals(1,resultado.conocimientos().getFirst().id());assertEquals("Paso autorizado 1",resultado.conocimientos().getFirst().instrucciones());
  assertFalse(resultado.mensaje().contains("INTERNO_NO_PUBLICAR"));
  // La revisión pública también se repite al preparar la salida.
  jdbc.update("update co_solucion set solucion_autoservicio=false where solucion_id=1");
  assertEquals(RespuestaCanal.Estado.SIN_RESULTADOS,preparador.preparar(resultado).estado());
  mvc.perform(post("/webhooks/whatsapp").contentType("application/json").header("X-Hub-Signature-256",WebhookHttpLocalTest.firma(imagen)).content(imagen))
    .andExpect(status().isOk()).andExpect(jsonPath("$.duplicados").value(1));
  assertEquals(2,jdbc.queryForObject("select count(*) from wa_evento",Integer.class));
 }
 @Test void firmaInvalidaNoProduceEfectos()throws Exception{
  int antes=jdbc.queryForObject("select count(*) from wa_evento",Integer.class);
  mvc.perform(post("/webhooks/whatsapp").contentType("application/json").header("X-Hub-Signature-256","sha256="+"0".repeat(64)).content(evento("wamid.qa-firma","text","Error"))).andExpect(status().isForbidden());
  assertEquals(antes,jdbc.queryForObject("select count(*) from wa_evento",Integer.class));
 }
}
