package cl.casol.backend.whatsapp;

import cl.casol.backend.whatsapp.application.port.out.AlmacenWhatsAppPort;
import cl.casol.backend.whatsapp.domain.*;
import cl.casol.backend.whatsapp.infrastructure.http.ValidacionWebhookLocal;
import cl.casol.backend.whatsapp.infrastructure.meta.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntradaMetaQaTest {
 static final String TOKEN="token-sintetico-meta-qa-local",SECRET="secreto-sintetico-meta-qa-local";
 static final String WABA="qa-waba-32a",NUMERO="qa-numero-32a",TEL="100000000000001",BSUID="US.qa32a";
 AlmacenWhatsAppPort almacen;
 ControlesMetaQa controles;
 ObservabilidadMetaQa observabilidad;
 EntradaMetaQaAdapter entrada;
 static final JsonMapper JSON=JsonMapper.builder().build();
 @BeforeEach void preparar(){
  almacen=mock(AlmacenWhatsAppPort.class);when(almacen.recibirLote(anyList(),anyList())).thenAnswer(i->new AlmacenWhatsAppPort.Recibo(((List<?>)i.getArgument(0)).size()+((List<?>)i.getArgument(1)).size(),0));
  observabilidad=new ObservabilidadMetaQa();controles=new ControlesMetaQa(new ControlesMetaQa.Estado(true,false,false,false),observabilidad);
  entrada=new EntradaMetaQaAdapter(almacen,new ValidacionWebhookLocal(TOKEN,SECRET,EntradaMetaQaAdapter.MAX_BYTES),
    new PoliticaMetaQa(WABA,NUMERO,"tel:"+TEL+"=qa-a,bsuid:"+BSUID+"=qa-a,tel:100000000000002=qa-b"),controles,observabilidad);
 }
 static Map<String,Object> texto(String id,String telefono,String bsuid,String contenido){
  var m=new LinkedHashMap<String,Object>();m.put("id",id);if(telefono!=null)m.put("from",telefono);if(bsuid!=null)m.put("from_user_id",bsuid);
  m.put("timestamp",Long.toString(Instant.now().getEpochSecond()));m.put("type","text");m.put("text",Map.of("body",contenido));return m;
 }
 static byte[] envolver(Map<String,Object> campos){
  var value=new LinkedHashMap<String,Object>();value.put("messaging_product","whatsapp");value.put("metadata",Map.of("phone_number_id",NUMERO));value.putAll(campos);
  return JSON.writeValueAsBytes(Map.of("object","whatsapp_business_account","entry",List.of(Map.of("id",WABA,"changes",List.of(Map.of("field","messages","value",value))))));
 }
 static byte[] evento(String id,String telefono,String bsuid,String contenido){return envolver(Map.of("messages",List.of(texto(id,telefono,bsuid,contenido))));}
 static String firma(byte[] b)throws Exception{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));return "sha256="+HexFormat.of().formatHex(m.doFinal(b));}
 EntradaMetaQaAdapter.Acuse recibir(byte[] b)throws Exception{return entrada.recibir(b,firma(b));}
 @Test void persistenciaEsObligatoria(){assertThrows(NullPointerException.class,()->new EntradaMetaQaAdapter(null,null,null,null,null));}
 @Test void telefonoYBsuidUsanIdentidadCanonica()throws Exception{
  recibir(evento("a",TEL,null,"Error"));recibir(evento("b",null,BSUID,"Error"));recibir(evento("c",TEL,BSUID,"Error"));
  @SuppressWarnings("unchecked") ArgumentCaptor<List<AlmacenWhatsAppPort.Entrada>> c=ArgumentCaptor.forClass(List.class);
  verify(almacen,times(3)).recibirLote(c.capture(),anyList());for(var lote:c.getAllValues())assertEquals("qa-a",lote.getFirst().mensaje().usuario());
 }
 @Test void identidadesDualesDiscordantesSeRechazan()throws Exception{
  byte[] b=evento("a","100000000000002",BSUID,"Error");assertThrows(PoliticaMetaQa.NoAutorizado.class,()->recibir(b));verifyNoInteractions(almacen);
 }
 @Test void activosYParticipantesSeValidanAntesDePersistir()throws Exception{
  String original=new String(evento("a",TEL,null,"Error"),java.nio.charset.StandardCharsets.UTF_8);
  for(String invalido:List.of(original.replace(WABA,"otra-waba"),original.replace(NUMERO,"otro-numero"),original.replace(TEL,"100000000000099"))){
   byte[] b=invalido.getBytes(java.nio.charset.StandardCharsets.UTF_8);assertThrows(PoliticaMetaQa.NoAutorizado.class,()->recibir(b));
  }verifyNoInteractions(almacen);
 }
 @Test void firmaSeValidaAntesDelParserYDeLosInterruptores(){
  controles.recepcion(false);assertThrows(ValidacionWebhookLocal.Rechazo.class,()->entrada.recibir("no JSON".getBytes(),"sha256="+"0".repeat(64)));verifyNoInteractions(almacen);
 }
 @Test void recepcionPausadaNoAceptaNiPersiste()throws Exception{
  controles.recepcion(false);assertThrows(EntradaMetaQaAdapter.Pausado.class,()->recibir(evento("a",TEL,null,"Error")));verifyNoInteractions(almacen);assertEquals(1L,observabilidad.instantanea().get("PAUSA"));
 }
 @Test void loteCompletoSeValidaAntesDeEfectos()throws Exception{
  var valido=texto("a",TEL,null,"Error");var invalido=texto("b","100000000000099",null,"Error");
  assertThrows(PoliticaMetaQa.NoAutorizado.class,()->recibir(envolver(Map.of("messages",List.of(valido,invalido)))));verifyNoInteractions(almacen);
 }
 @Test void aceptaTextosYLotesMayoresAlPrototipo()throws Exception{
  var lista=new ArrayList<Map<String,Object>>();for(int i=0;i<12;i++)lista.add(texto("a"+i,TEL,null,"x".repeat(4096)));
  assertEquals(12,recibir(envolver(Map.of("messages",lista))).aceptados());verify(almacen).recibirLote(argThat(l->l.size()==12),anyList());
 }
 @Test void tamanoTotalTieneLimite(){
  assertThrows(ValidacionWebhookLocal.Rechazo.class,()->entrada.recibir(new byte[EntradaMetaQaAdapter.MAX_BYTES+1],"sha256="+"0".repeat(64)));verifyNoInteractions(almacen);
 }
 @Test void rechazaClavesDuplicadasYJsonAdicional()throws Exception{
  String original=new String(evento("a",TEL,null,"Error"),java.nio.charset.StandardCharsets.UTF_8);
  for(String invalido:List.of(original+" {}",original.replace("\"object\":","\"object\":\"otro\",\"object\":"))){
   byte[] b=invalido.getBytes(java.nio.charset.StandardCharsets.UTF_8);assertThrows(ValidacionWebhookLocal.Rechazo.class,()->recibir(b));
  }verifyNoInteractions(almacen);
 }
 @Test void estadosSeEntreganConMensajesEnUnaOperacion()throws Exception{
  var estado=Map.of("id","wamid.salida","recipient_user_id",BSUID,"status","delivered","timestamp",Instant.now().getEpochSecond());
  var ack=recibir(envolver(Map.of("messages",List.of(texto("a",TEL,null,"Error")),"statuses",List.of(estado))));
  assertEquals(2,ack.aceptados());assertEquals(1,ack.estados());verify(almacen).recibirLote(argThat(l->l.size()==1),argThat(l->l.size()==1&&l.getFirst().participante().equals("qa-a")));
 }
 @Test void tiposDesconocidosNoSeInterpretanComoConsulta()throws Exception{
  var m=texto("a",TEL,null,"Error");m.put("type","document");recibir(envolver(Map.of("messages",List.of(m))));
  verify(almacen).recibirLote(argThat(l->l.getFirst().mensaje().tipo()==MensajeCanal.Tipo.NO_COMPATIBLE&&l.getFirst().mensaje().contenido().isEmpty()),anyList());
 }
 @Test void imagenEsSoloUnaReferenciaYNoSeDescarga()throws Exception{
  var m=texto("a",TEL,null,"ignorado");m.put("type","image");m.put("image",Map.of("id","medio-sintetico","mime_type","image/png"));
  recibir(envolver(Map.of("messages",List.of(m))));verify(almacen).recibirLote(argThat(l->l.getFirst().mensaje().tipo()==MensajeCanal.Tipo.IMAGEN),anyList());
 }
 @Test void mysqlNoDisponibleNuncaProduceAcuse()throws Exception{
  when(almacen.recibirLote(anyList(),anyList())).thenThrow(new IllegalStateException("NO_PUBLICAR_DATOS_SQL"));
  var e=assertThrows(ValidacionWebhookLocal.Rechazo.class,()->recibir(evento("a",TEL,null,"Error")));assertEquals(ValidacionWebhookLocal.Motivo.INTERNO,e.motivo());assertFalse(e.toString().contains("NO_PUBLICAR"));assertEquals(1L,observabilidad.instantanea().get("ALMACEN"));
 }
 @Test void interruptoresSonIndependientes(){
  controles.medios(true);assertTrue(controles.medios());assertFalse(controles.procesamiento());assertFalse(controles.envio());assertTrue(controles.recepcion());
  controles.envio(true);controles.recepcion(false);assertTrue(controles.envio());assertFalse(controles.recepcion());
 }
 @Test void metricasNoTienenEtiquetasControladasPorElMensaje()throws Exception{
  recibir(evento("ID_SECRETO",TEL,null,"TEXTO_SENSIBLE"));String m=observabilidad.instantanea().toString();
  for(String secreto:List.of("ID_SECRETO",TEL,"TEXTO_SENSIBLE",TOKEN,SECRET))assertFalse(m.contains(secreto));assertEquals(1L,observabilidad.instantanea().get("ACEPTADOS"));
 }
}
