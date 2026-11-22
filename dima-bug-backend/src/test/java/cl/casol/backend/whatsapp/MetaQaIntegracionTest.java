package cl.casol.backend.whatsapp;

import cl.casol.backend.whatsapp.infrastructure.meta.*;
import cl.casol.backend.whatsapp.application.port.out.*;
import cl.casol.backend.whatsapp.domain.RespuestaCanal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:wa_meta32a;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never",
 "jwt.secret=clave-sintetica-solo-whatsapp-qa-12345678901234567890123456789","jwt.expiration=3600000",
 "dimabug.automatizacion.modo=SIMULADO","dimabug.automatizacion.intervalo-ms=3600000",
 "dimabug.auth.correo.habilitado=false","dimabug.automatizacion.seguimiento-habilitado=false",
 "dimabug.whatsapp.meta-qa.verify-token=token-sintetico-meta-qa-local",
 "dimabug.whatsapp.meta-qa.app-secret=secreto-sintetico-meta-qa-local",
 "dimabug.whatsapp.meta-qa.waba=qa-waba-32a","dimabug.whatsapp.meta-qa.numero=qa-numero-32a",
 "dimabug.whatsapp.meta-qa.participantes=tel:100000000000001=qa-a,bsuid:US.qa32a=qa-a",
 "dimabug.whatsapp.qa.raiz=..","dimabug.whatsapp.durable.sondeo-ms=100",
 "dimabug.whatsapp.durable.clave-cifrado=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
 "dimabug.whatsapp.durable.clave-hmac=MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI="})
@ActiveProfiles("qa-whatsapp-meta")
@AutoConfigureMockMvc
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetaQaIntegracionTest {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 @Autowired ControlesMetaQa controles;
 @Autowired ObservabilidadMetaQa metricas;
 @MockitoBean(name="ocrWebhookHttpLocal") ReconocerImagenPort ocr;
 @MockitoSpyBean AlmacenWhatsAppPort almacen;
 @BeforeAll void comprobarDefaultsDeArranque(){assertEquals(new ControlesMetaQa.Estado(false,false,false,false),controles.instantanea());}
 @BeforeEach void limpiar(){
  controles.recepcion(false);controles.procesamiento(false);controles.medios(false);controles.envio(false);
  jdbc.update("delete from wa_respuesta");jdbc.update("delete from wa_evento");jdbc.update("delete from wa_conversacion");
  when(ocr.reconocer(anyString())).thenReturn(List.of(new RespuestaCanal.Candidato("Error E32",List.of())));
 }
 org.springframework.test.web.servlet.ResultActions enviar(byte[] b)throws Exception{
  return mvc.perform(post("/webhooks/whatsapp").contentType("application/json").header("X-Hub-Signature-256",EntradaMetaQaTest.firma(b)).content(b));
 }
 byte[] texto(String id,String tel,String bs,String texto){return EntradaMetaQaTest.evento(id,tel,bs,texto);}
 int contar(String tabla){return jdbc.queryForObject("select count(*) from "+tabla,Integer.class);}
 void esperar(String sql,int esperado)throws Exception{
  long fin=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
  while(System.nanoTime()<fin){if(jdbc.queryForObject(sql,Integer.class)==esperado)return;Thread.sleep(25);}fail("El estado esperado no se alcanzó");
 }
 @Test void valoresPorDefectoMantienenTodoPausado()throws Exception{
  assertFalse(controles.recepcion());assertFalse(controles.procesamiento());assertFalse(controles.medios());assertFalse(controles.envio());
  enviar(texto("a",EntradaMetaQaTest.TEL,null,"Error")).andExpect(status().isServiceUnavailable());assertEquals(0,contar("wa_evento"));
 }
 @Test void verificacionYSeguridadPermanecenLocales()throws Exception{
  mvc.perform(get("/webhooks/whatsapp").param("hub.mode","subscribe").param("hub.verify_token",EntradaMetaQaTest.TOKEN).param("hub.challenge","00123")).andExpect(status().isOk()).andExpect(content().string("00123"));
  mvc.perform(get("/webhooks/whatsapp").param("hub.mode","subscribe").param("hub.verify_token","malo").param("hub.challenge","x")).andExpect(status().isForbidden());
  byte[] b=texto("a",EntradaMetaQaTest.TEL,null,"Error");
  mvc.perform(post("/webhooks/whatsapp").with(r->{r.setRemoteAddr("192.0.2.1");return r;}).contentType("application/json").content(b).header("X-Hub-Signature-256",EntradaMetaQaTest.firma(b)).header("X-Forwarded-For","127.0.0.1")).andExpect(status().isForbidden());
  for(var metodo:List.of(put("/webhooks/whatsapp"),delete("/webhooks/whatsapp"),head("/webhooks/whatsapp"),options("/webhooks/whatsapp")))mvc.perform(metodo).andExpect(status().isForbidden());
  mvc.perform(get("/api/conocimientos")).andExpect(status().isUnauthorized());assertEquals(0,contar("wa_evento"));
 }
 @Test void recepcionDurableYProcesamientoSeActivanSeparadamente()throws Exception{
  controles.recepcion(true);byte[] b=texto("a",EntradaMetaQaTest.TEL,null,"Error E32");
  enviar(b).andExpect(status().isOk()).andExpect(jsonPath("$.aceptados").value(1));
  assertEquals("PENDIENTE",jdbc.queryForObject("select estado from wa_evento",String.class));assertEquals(0,contar("wa_respuesta"));
  enviar(b).andExpect(status().isOk()).andExpect(jsonPath("$.duplicados").value(1));
  controles.procesamiento(true);esperar("select count(*) from wa_evento where estado='TERMINADO'",1);
  assertEquals("PENDIENTE",jdbc.queryForObject("select estado from wa_respuesta",String.class));
  controles.envio(true);esperar("select count(*) from wa_respuesta where estado='SIMULADA'",1);verifyNoInteractions(ocr);
 }
 @Test void mediosPausadosConservanImagenYBsuidConfirmaMismaConversacion()throws Exception{
  controles.recepcion(true);controles.procesamiento(true);
  var m=EntradaMetaQaTest.texto("imagen",EntradaMetaQaTest.TEL,null,"ignorado");m.put("type","image");m.put("image",Map.of("id","qa-media-legible","mime_type","image/png"));
  enviar(EntradaMetaQaTest.envolver(Map.of("messages",List.of(m)))).andExpect(status().isOk());
  assertEquals("PENDIENTE",jdbc.queryForObject("select estado from wa_evento",String.class));verifyNoInteractions(ocr);
  controles.medios(true);esperar("select count(*) from wa_evento where resultado_codigo='CONFIRMACION'",1);
  String ref=jdbc.queryForObject("select referencia from wa_conversacion",String.class);
  enviar(texto("confirmacion",null,EntradaMetaQaTest.BSUID,"CONFIRMAR "+ref)).andExpect(status().isOk());
  esperar("select count(*) from wa_evento where estado='TERMINADO'",2);assertEquals(1,contar("wa_conversacion"));verify(ocr,times(1)).reconocer("qa-media-legible");
 }
 @Test void estadosSePersistenSinGenerarRespuesta()throws Exception{
  controles.recepcion(true);controles.procesamiento(true);controles.medios(true);controles.envio(true);
  byte[] b=EntradaMetaQaTest.envolver(Map.of("statuses",List.of(Map.of("id","wamid.sintetico","recipient_user_id",EntradaMetaQaTest.BSUID,"status","read","timestamp",Long.toString(Instant.now().getEpochSecond())))));
  enviar(b).andExpect(status().isOk()).andExpect(jsonPath("$.estados").value(1));enviar(b).andExpect(status().isOk()).andExpect(jsonPath("$.duplicados").value(1));
  assertEquals(1,contar("wa_evento"));assertEquals(0,contar("wa_conversacion"));assertEquals(0,contar("wa_respuesta"));
  assertEquals("ESTADO_SIN_CORRELACION",jdbc.queryForObject("select resultado_codigo from wa_evento",String.class));verifyNoInteractions(ocr);
 }
 @Test void baseNoDisponibleDevuelve503SinDatosInternos()throws Exception{
  controles.recepcion(true);doThrow(new IllegalStateException("CREDENCIAL_NO_PUBLICAR")).when(almacen).recibirLote(anyList(),anyList());
  enviar(texto("a",EntradaMetaQaTest.TEL,null,"Error")).andExpect(status().isServiceUnavailable()).andExpect(content().json("{\"estado\":\"RECHAZADO\"}"));assertEquals(0,contar("wa_evento"));
 }
 @Test void textoLargoSeAceptaDurablementePeroNoSeBusca()throws Exception{
  controles.recepcion(true);controles.procesamiento(true);
  enviar(texto("a",EntradaMetaQaTest.TEL,null,"x".repeat(4096))).andExpect(status().isOk());esperar("select count(*) from wa_evento where resultado_codigo='INVALIDO'",1);
 }
 @Test void logsYMetricasNoContienenDatosDelEvento()throws Exception{
  var root=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
  var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();appender.start();root.addAppender(appender);
  var dispatcher=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("org.springframework.web.servlet.DispatcherServlet");
  var nivel=dispatcher.getLevel();dispatcher.setLevel(ch.qos.logback.classic.Level.DEBUG);
  try{
   controles.recepcion(true);byte[] b=texto("MARCADOR_ID_PRIVADO",EntradaMetaQaTest.TEL,null,"MARCADOR_TEXTO_PRIVADO");enviar(b).andExpect(status().isOk());
   enviar(texto("otro","100000000000099",null,"NO_PUBLICAR")).andExpect(status().isForbidden());
   String logs=appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).collect(java.util.stream.Collectors.joining())+metricas.instantanea();
   for(String s:List.of(EntradaMetaQaTest.TEL,EntradaMetaQaTest.TOKEN,EntradaMetaQaTest.SECRET,"MARCADOR_ID_PRIVADO","MARCADOR_TEXTO_PRIVADO","100000000000099","NO_PUBLICAR",EntradaMetaQaTest.firma(b)))assertFalse(logs.contains(s));
  }finally{dispatcher.setLevel(nivel);root.detachAppender(appender);appender.stop();}
 }
}
