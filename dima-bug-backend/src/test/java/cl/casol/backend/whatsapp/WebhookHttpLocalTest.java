package cl.casol.backend.whatsapp;

import cl.casol.backend.whatsapp.infrastructure.http.*;
import cl.casol.backend.whatsapp.application.port.out.ReconocerImagenPort;
import cl.casol.backend.whatsapp.domain.RespuestaCanal;
import cl.casol.backend.conocimiento.application.service.ConsultarChatbotService;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.infrastructure.security.*;
import cl.casol.backend.shared.infrastructure.security.SecurityConfig;
import cl.casol.backend.shared.application.service.VisibilidadContenidoService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=WebhookWhatsAppController.class,properties={
 "dimabug.whatsapp.http-local.verify-token=token-sintetico-webhook-local-2026",
 "dimabug.whatsapp.http-local.app-secret=secreto-sintetico-webhook-local-2026",
 "dimabug.whatsapp.qa.raiz=.."})
@ActiveProfiles("qa-whatsapp-http-local")
@Import({WebhookHttpLocalConfig.class,SecurityConfig.class,JwtAuthenticationFilter.class})
class WebhookHttpLocalTest {
 static final String TOKEN="token-sintetico-webhook-local-2026",SECRET="secreto-sintetico-webhook-local-2026";
 static final AtomicInteger SECUENCIA=new AtomicInteger();
 @Autowired MockMvc mvc;
 @Autowired @org.springframework.beans.factory.annotation.Qualifier("seguridadWebhookHttpLocal") org.springframework.security.web.SecurityFilterChain cadenaWebhook;
 @MockitoBean ConsultarChatbotService buscador;
 @MockitoBean(name="ocrWebhookHttpLocal") ReconocerImagenPort ocr;
 @MockitoBean TokenServicePort tokens;
 @MockitoBean UsuarioRepository usuarios;
 @MockitoBean UsuarioAutenticadoCache cache;
 @MockitoBean VisibilidadContenidoService visibilidad;
 String usuario;
 @BeforeEach void preparar(){
   usuario="100000000000"+SECUENCIA.incrementAndGet();
   when(buscador.consultar(anyString())).thenReturn(List.of(new ConsultarChatbotService.SolucionChatbot(8,"INTERNO_NO_PUBLICAR","Solución pública","Pasos publicados")));
   when(ocr.reconocer(anyString())).thenReturn(List.of(new RespuestaCanal.Candidato("Error 0x0000011b",List.of("Comprueba el código"))));
 }
 static String firma(byte[] bytes)throws Exception {
   Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
   return "sha256="+HexFormat.of().formatHex(mac.doFinal(bytes));
 }
 byte[] evento(String tipo,String contenido){
   var detalle=tipo.equals("text")?Map.of("body",contenido):Map.of("id",contenido,"mime_type","image/png");
   return envolver(Map.of("messages",List.of(Map.of("id","wamid.HTTP_"+SECUENCIA.incrementAndGet(),"from",usuario,
       "timestamp","1794646800","type",tipo,tipo,detalle))));
 }
 byte[] envolver(Map<String,Object> campos){
   var valor=new LinkedHashMap<String,Object>();valor.put("messaging_product","whatsapp");valor.put("metadata",Map.of("phone_number_id","100000000000001"));valor.putAll(campos);
   return JsonMapper.builder().build().writeValueAsBytes(Map.of("object","whatsapp_business_account","entry",List.of(Map.of(
       "id","100000000000002","changes",List.of(Map.of("field","messages","value",valor))))));
 }
 MockHttpServletRequestBuilder firmado(byte[] b)throws Exception {
   return post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(b).header("X-Hub-Signature-256",firma(b));
 }
 MockHttpServletRequestBuilder getValido(){return get("/webhooks/whatsapp").queryParam("hub.mode","subscribe").queryParam("hub.verify_token",TOKEN).queryParam("hub.challenge","001234");}
 @Test void getValidoSinJwt()throws Exception {mvc.perform(getValido()).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN)).andExpect(content().string("001234")).andExpect(header().string("Cache-Control","no-store"));verifyNoInteractions(buscador,ocr,tokens);}
 @Test void getTokenIncorrecto()throws Exception {mvc.perform(get("/webhooks/whatsapp").param("hub.mode","subscribe").param("hub.verify_token","incorrecto").param("hub.challenge","x")).andExpect(status().isForbidden());verifyNoInteractions(buscador,ocr);}
 @Test void getParametrosAusentes()throws Exception {for(String omitido:List.of("hub.mode","hub.verify_token","hub.challenge")){var r=get("/webhooks/whatsapp");for(var e:Map.of("hub.mode","subscribe","hub.verify_token",TOKEN,"hub.challenge","x").entrySet())if(!e.getKey().equals(omitido))r.param(e.getKey(),e.getValue());mvc.perform(r).andExpect(status().isForbidden());}}
 @Test void getModoIncorrectoYParametrosRepetidos()throws Exception {mvc.perform(get("/webhooks/whatsapp").param("hub.mode","other").param("hub.verify_token",TOKEN).param("hub.challenge","x")).andExpect(status().isForbidden());mvc.perform(getValido().param("hub.challenge","otro")).andExpect(status().isForbidden());}
 @Test void challengeExactoConEspaciosYCeros()throws Exception {String valor=" 000123 + á ";mvc.perform(get("/webhooks/whatsapp").param("hub.mode","subscribe").param("hub.verify_token",TOKEN).param("hub.challenge",valor)).andExpect(status().isOk()).andExpect(content().string(valor));}
 @Test void postFirmaValidaYTexto()throws Exception {mvc.perform(firmado(evento("text","Error de impresión"))).andExpect(status().isOk()).andExpect(jsonPath("$.respuestas[0].estado").value("RESULTADOS")).andExpect(jsonPath("$.respuestas[0].conocimientos[0].id").value(8)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("INTERNO_NO_PUBLICAR"))));verify(buscador).consultar("Error de impresión");}
 @Test void postSinFirma()throws Exception {mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(evento("text","Error"))).andExpect(status().isForbidden());verifyNoInteractions(buscador,ocr);}
 @Test void firmaIncorrectaOMalformada()throws Exception {for(String f:List.of("sha256="+"00".repeat(32),"sha1=abcd","sha256=abc","sha256="+"gg".repeat(32),"")){mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(evento("text","Error")).header("X-Hub-Signature-256",f)).andExpect(status().isForbidden());}verifyNoInteractions(buscador,ocr);}
 @Test void firmaDuplicada()throws Exception {var b=evento("text","Error");mvc.perform(firmado(b).header("X-Hub-Signature-256",firma(b))).andExpect(status().isForbidden());verifyNoInteractions(buscador);}
 @Test void cuerpoAlteradoRechazado()throws Exception {byte[] original=evento("text","Error");byte[] alterado=Arrays.copyOf(original,original.length+1);alterado[alterado.length-1]=' ';mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(alterado).header("X-Hub-Signature-256",firma(original))).andExpect(status().isForbidden());verifyNoInteractions(buscador,ocr);}
 @Test void firmaValidaSobreBytesSinReserializar()throws Exception {byte[] b=(" \n"+new String(evento("text","Error áé 0x0000011b"),StandardCharsets.UTF_8)+" ").getBytes(StandardCharsets.UTF_8);mvc.perform(firmado(b)).andExpect(status().isOk());verify(buscador).consultar("Error áé 0x0000011b");}
 @Test void jsonInvalidoFirmado()throws Exception {for(String v:List.of("{","null","{}","{}{}","{\"object\":1,\"object\":2}")){mvc.perform(firmado(v.getBytes(StandardCharsets.UTF_8))).andExpect(status().isBadRequest());}verifyNoInteractions(buscador,ocr);}
 @Test void firmaSeValidaAntesDeParsear()throws Exception {mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content("{").header("X-Hub-Signature-256","sha256="+"0".repeat(64))).andExpect(status().isForbidden());verifyNoInteractions(buscador,ocr);}
 @Test void imagenPideConfirmacionSinBuscar()throws Exception {mvc.perform(firmado(evento("image","100000000000003"))).andExpect(status().isOk()).andExpect(jsonPath("$.respuestas[0].estado").value("CONFIRMACION")).andExpect(jsonPath("$.respuestas[0].candidatos[0].texto").value("Error 0x0000011b"));verify(ocr).reconocer("100000000000003");verifyNoInteractions(buscador);}
 @Test void imagenConfirmadaReutilizaBuscador()throws Exception {var r=mvc.perform(firmado(evento("image","100000000000003"))).andReturn();String ref=JsonMapper.builder().build().readTree(r.getResponse().getContentAsByteArray()).path("respuestas").get(0).path("referencia").asText();mvc.perform(firmado(evento("text","CONFIRMAR "+ref))).andExpect(status().isOk()).andExpect(jsonPath("$.respuestas[0].estado").value("RESULTADOS"));verify(buscador).consultar("Error 0x0000011b");}
 @Test void entregaNoLlegaAlCasoDeBusqueda()throws Exception {var b=envolver(Map.of("statuses",List.of(Map.of("id","wamid.HTTP_estado","recipient_id",usuario,"timestamp","1794646800","status","delivered"))));mvc.perform(firmado(b)).andExpect(status().isOk()).andExpect(jsonPath("$.estados").value(1)).andExpect(jsonPath("$.respuestas").isEmpty());verifyNoInteractions(buscador,ocr);}
 @Test void estadoDesconocidoSeIgnora()throws Exception {var b=envolver(Map.of("statuses",List.of(Map.of("id","wamid.HTTP_estado","recipient_id",usuario,"timestamp","1794646800","status","futuro"))));mvc.perform(firmado(b)).andExpect(status().isOk()).andExpect(jsonPath("$.ignorados").value(1));verifyNoInteractions(buscador,ocr);}
 @Test void mensajeNoCompatible()throws Exception {mvc.perform(firmado(evento("audio","100000000000004"))).andExpect(status().isOk()).andExpect(jsonPath("$.respuestas[0].estado").value("NO_COMPATIBLE"));verifyNoInteractions(buscador,ocr);}
 @Test void campoDesconocidoIgnorado()throws Exception {byte[] b=JsonMapper.builder().build().writeValueAsBytes(Map.of("object","whatsapp_business_account","entry",List.of(Map.of("id","100000000000002","changes",List.of(Map.of("field","campo_futuro","value",Map.of("dato","sintetico")))))));mvc.perform(firmado(b)).andExpect(status().isOk()).andExpect(jsonPath("$.ignorados").value(1));verifyNoInteractions(buscador,ocr);}
 @Test void demasiadoGrande()throws Exception {byte[] b=new byte[ValidacionWebhookLocal.MAX_BYTES+1];mvc.perform(firmado(b)).andExpect(status().isPayloadTooLarge());verifyNoInteractions(buscador,ocr);}
 @Test void limiteDuranteLecturaSinLongitudDeclarada()throws Exception {
   byte[] b=new byte[ValidacionWebhookLocal.MAX_BYTES+1];String sign=firma(b);
   mvc.perform(firmado(b).with(r->{
     var streaming=new org.springframework.mock.web.MockHttpServletRequest(r.getServletContext()){
       @Override public long getContentLengthLong(){return -1;}
       @Override public int getContentLength(){return -1;}
     };
     streaming.setMethod("POST");streaming.setRequestURI("/webhooks/whatsapp");streaming.setRemoteAddr("127.0.0.1");
     streaming.setContentType(MediaType.APPLICATION_JSON_VALUE);streaming.setContent(b);
     streaming.addHeader("X-Hub-Signature-256",sign);streaming.addHeader("Transfer-Encoding","chunked");return streaming;
   })).andExpect(status().isPayloadTooLarge());verifyNoInteractions(buscador,ocr);
 }
 @Test void duplicadoNoBuscaDosVeces()throws Exception {byte[] b=evento("text","Error duplicado");mvc.perform(firmado(b)).andExpect(status().isOk());mvc.perform(firmado(b)).andExpect(status().isOk()).andExpect(jsonPath("$.respuestas[0].estado").value("DUPLICADO"));verify(buscador,times(1)).consultar("Error duplicado");}
 @Test void erroresOcrYBuscadorControlados()throws Exception {when(ocr.reconocer(anyString())).thenThrow(new IllegalStateException("secreto-interno"));mvc.perform(firmado(evento("image","100000000000003"))).andExpect(status().isOk()).andExpect(jsonPath("$.respuestas[0].estado").value("ERROR_OCR")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secreto-interno"))));when(buscador.consultar(anyString())).thenThrow(new IllegalStateException("secreto-interno"));mvc.perform(firmado(evento("text","Error"))).andExpect(status().isOk()).andExpect(jsonPath("$.respuestas[0].estado").value("ERROR_BUSCADOR"));}
 @Test void remotoRechazadoAunqueFirmaValidaEIgnoreForwarded()throws Exception {mvc.perform(firmado(evento("text","Error")).with(r->{r.setRemoteAddr("192.0.2.20");return r;}).header("X-Forwarded-For","127.0.0.1")).andExpect(status().isForbidden());verifyNoInteractions(buscador,ocr);}
 @Test void soloGetYPostEnRutaExacta()throws Exception {for(var r:List.of(put("/webhooks/whatsapp"),delete("/webhooks/whatsapp"),patch("/webhooks/whatsapp"),head("/webhooks/whatsapp"),options("/webhooks/whatsapp")))mvc.perform(r).andExpect(status().isForbidden());mvc.perform(get("/webhooks/whatsapp/otra")).andExpect(status().isUnauthorized());mvc.perform(get("/webhooks/whatsapp/")).andExpect(status().isUnauthorized());}
 @Test void otrosEndpointsSiguenProtegidos()throws Exception {for(String ruta:List.of("/api/conocimientos","/api/admin/prueba","/api/tickets","/webhooks/otro"))mvc.perform(get(ruta)).andExpect(status().isUnauthorized());mvc.perform(post("/api/chatbot/consultas").contentType(MediaType.APPLICATION_JSON).content("{\"consulta\":\"Error\"}")).andExpect(status().isUnauthorized());}
 @Test void loteIncompletoNoProduceEfectos()throws Exception {var b=evento("text","Error");String json=new String(b,StandardCharsets.UTF_8).replace("\"messages\":[","\"messages\":[{},");mvc.perform(firmado(json.getBytes(StandardCharsets.UTF_8))).andExpect(status().isBadRequest());verifyNoInteractions(buscador,ocr);}
 @Test void noSeRegistranSecretosNiContenido()throws Exception {
   var root=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
   var dispatcher=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("org.springframework.web.servlet.DispatcherServlet");
   var nivelAnterior=dispatcher.getLevel();dispatcher.setLevel(ch.qos.logback.classic.Level.DEBUG);
   var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();appender.start();root.addAppender(appender);
   try{byte[] b=evento("text","MARCADOR_SENSIBLE_HTTP_QA");mvc.perform(getValido()).andExpect(status().isOk());mvc.perform(firmado(b)).andExpect(status().isOk());mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(b).header("X-Hub-Signature-256","invalida")).andExpect(status().isForbidden());String logs=appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).collect(java.util.stream.Collectors.joining());for(String secreto:List.of(TOKEN,SECRET,"MARCADOR_SENSIBLE_HTTP_QA",usuario,firma(b)))assertFalse(logs.contains(secreto));}
   finally{root.detachAppender(appender);appender.stop();dispatcher.setLevel(nivelAnterior);}
 }

 @Test void imagenFixtureEjecutaOcrNodeRealSinDescargas()throws Exception {
   doAnswer(inv->new cl.casol.backend.whatsapp.infrastructure.ocr.OcrNodeProcesoAdapter(java.nio.file.Path.of("..")).reconocer(inv.getArgument(0)))
       .when(ocr).reconocer("qa-media-legible");
   mvc.perform(firmado(evento("image","qa-media-legible"))).andExpect(status().isOk())
       .andExpect(jsonPath("$.respuestas[0].estado").value("CONFIRMACION"))
       .andExpect(jsonPath("$.respuestas[0].candidatos[0].texto").value("Error 0x0000011b"));
   verifyNoInteractions(buscador);
 }
 @Test void referenciaDeMedioSinFixtureNoDescargaNada()throws Exception {
   doAnswer(inv->new cl.casol.backend.whatsapp.infrastructure.ocr.OcrNodeProcesoAdapter(java.nio.file.Path.of("..")).reconocer(inv.getArgument(0)))
       .when(ocr).reconocer("100000000000099");
   mvc.perform(firmado(evento("image","100000000000099"))).andExpect(status().isOk())
       .andExpect(jsonPath("$.respuestas[0].estado").value("ERROR_OCR"));verifyNoInteractions(buscador);
 }
 @Test void limiteExactoEsAceptadoSinTruncarElCuerpo()throws Exception {
   byte[] original=evento("text","Error límite");byte[] b=Arrays.copyOf(original,ValidacionWebhookLocal.MAX_BYTES);
   Arrays.fill(b,original.length,b.length,(byte)' ');
   mvc.perform(firmado(b)).andExpect(status().isOk());verify(buscador).consultar("Error límite");
 }
 @Test void loteDemasiadoNumerosoEImagenNoPermitidaSeRechazanAntesDeBuscar()throws Exception {
   String evento=new String(evento("text","Error"),StandardCharsets.UTF_8);
   var nodo=JsonMapper.builder().build().readTree(evento);
   var mensaje=nodo.path("entry").get(0).path("changes").get(0).path("value").path("messages").get(0);
   byte[] grande=envolver(Map.of("messages",Collections.nCopies(11,mensaje)));
   mvc.perform(firmado(grande)).andExpect(status().isBadRequest());
   byte[] imagen=new String(evento("image","100000000000003"),StandardCharsets.UTF_8).replace("image/png","image/svg+xml").getBytes(StandardCharsets.UTF_8);
   mvc.perform(firmado(imagen)).andExpect(status().isBadRequest());verifyNoInteractions(buscador,ocr);
 }

 @Test void cadenaEspecificaConservaFiltroCsrf() {
   assertTrue(cadenaWebhook.getFilters().stream().anyMatch(org.springframework.security.web.csrf.CsrfFilter.class::isInstance));
   assertFalse(cadenaWebhook.getFilters().stream().anyMatch(JwtAuthenticationFilter.class::isInstance));
 }
}
