package cl.casol.backend.whatsapp;

import cl.casol.backend.whatsapp.application.port.out.*;
import cl.casol.backend.whatsapp.application.service.*;
import cl.casol.backend.whatsapp.domain.*;
import cl.casol.backend.whatsapp.infrastructure.persistence.*;
import cl.casol.backend.whatsapp.infrastructure.http.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles({"qa-whatsapp-http-local","qa-whatsapp-durable","qa-whatsapp-test-store"})
@SpringBootTest(classes=PersistenciaWhatsAppTest.Contexto.class,properties={
 "spring.datasource.url=jdbc:h2:mem:wa_durable;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.sql.init.mode=always","spring.jpa.hibernate.ddl-auto=validate",
 "spring.jpa.mapping-resources=META-INF/whatsapp.orm.xml"})
class PersistenciaWhatsAppTest {
 @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
 @Profile("qa-whatsapp-test-store")
 @EntityScan(basePackageClasses=ConversacionWhatsAppEntity.class)
 @EnableJpaRepositories(basePackageClasses=ConversacionWhatsAppRepository.class)
 @Import(AlmacenWhatsAppJpaAdapter.class)
 static class Contexto {
  @Bean CifradoWhatsApp crypto(){return new CifradoWhatsApp(Base64.getEncoder().encodeToString(new byte[32]),Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes()));}
 }
 @DynamicPropertySource static void esquema(DynamicPropertyRegistry r)throws Exception{
  String url=System.getenv("WA_QA_JDBC_URL");
  if(url!=null){
   if(!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/wa_durable_[a-f0-9]+\\?.*"))throw new IllegalStateException("Base QA no aislada");
   r.add("spring.datasource.url",()->url);r.add("spring.datasource.username",()->"wa_worker");
   r.add("spring.datasource.password",()->System.getenv("WA_QA_JDBC_PASSWORD"));
   r.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");r.add("spring.sql.init.mode",()->"never");return;
  }
  // Se deriva del SQL MySQL aprobado: no existe una segunda definición de tablas.
  String sql=Files.readString(Path.of("../qa/whatsapp/mysql-stage22a/schema.sql"))
   .replaceAll("CHARACTER SET \\w+ COLLATE \\w+","")
   .replaceAll("ENGINE=InnoDB DEFAULT  COLLATE utf8mb4_bin","")
   .replaceAll("ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin","")
   .replaceAll("ENGINE=InnoDB DEFAULT\\s*","");
  Path destino=Path.of("../qa/runtime/whatsapp-durable/schema-h2.sql").toAbsolutePath();Files.createDirectories(destino.getParent());Files.writeString(destino,sql);
  r.add("spring.sql.init.schema-locations",()->destino.toUri().toString());
 }
 @Autowired AlmacenWhatsAppPort almacen;
 @Autowired JdbcTemplate jdbc;
 @Autowired CifradoWhatsApp crypto;
 @BeforeEach void limpiar(TestInfo info){
  if(info.getTestMethod().orElseThrow().getName().startsWith("reinicio"))return;
  jdbc.update("delete from wa_respuesta");jdbc.update("delete from wa_evento");jdbc.update("delete from wa_conversacion");
 }
 MensajeCanal mensaje(String id,String usuario,MensajeCanal.Tipo tipo,String texto){return new MensajeCanal("qa-cuenta-a","qa-canal-a",usuario,id,tipo,texto);}
 void recibir(MensajeCanal m){almacen.recibir(List.of(new AlmacenWhatsAppPort.Entrada(m,Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))));}
 ProcesarReservaWhatsAppService procesador(){return new ProcesarReservaWhatsAppService(almacen,q->List.of(new RespuestaCanal.Solucion(1,"Pública","Paso autorizado")),ref->List.of(new RespuestaCanal.Candidato("Error E42",List.of())));}
 int contar(String tabla){return jdbc.queryForObject("select count(*) from "+tabla,Integer.class);}
 @Test void confirmacionSobreviveAUnaNuevaInstancia(){
  recibir(mensaje("a","u",MensajeCanal.Tipo.IMAGEN,"qa-media-legible"));var r=almacen.reservar(true);procesador().ejecutar(r);
  var propuesta=almacen.reservarSalida();assertEquals(RespuestaCanal.Estado.CONFIRMACION,propuesta.respuesta().estado());assertTrue(almacen.simular(propuesta,propuesta.respuesta()));
  recibir(mensaje("b","u",MensajeCanal.Tipo.TEXTO,"CONFIRMAR "+propuesta.respuesta().referencia()));
  procesador().ejecutar(almacen.reservar(false));var resultado=almacen.reservarSalida();assertEquals(RespuestaCanal.Estado.RESULTADOS,resultado.respuesta().estado());
  assertTrue(almacen.simular(resultado,resultado.respuesta()));assertEquals(2,contar("wa_respuesta"));
  assertNull(jdbc.queryForObject("select estado_cifrado from wa_conversacion",byte[].class));
  assertEquals(0,jdbc.queryForObject("select count(*) from wa_evento where contenido_cifrado is not null",Integer.class));
 }
 @Test void duplicadosYConflictosNoSobrescriben(){
  var m=mensaje("a","u",MensajeCanal.Tipo.TEXTO,"Error E42");var e=new AlmacenWhatsAppPort.Entrada(m,Instant.now());
  assertEquals(1,almacen.recibir(List.of(e)).aceptados());assertEquals(1,almacen.recibir(List.of(e)).duplicados());
  assertThrows(AlmacenWhatsAppPort.EventoConflictivo.class,()->almacen.recibir(List.of(new AlmacenWhatsAppPort.Entrada(mensaje("a","u",MensajeCanal.Tipo.TEXTO,"distinto"),e.emitido()))));assertEquals(1,contar("wa_evento"));
 }
 @Test void estadosSonDurablesDeduplicadosYSinConversacion(){
  var estado=new EstadoEntregaCanal("qa-cuenta","qa-numero","wamid.salida","qa-a","read",Instant.now());
  assertEquals(1,almacen.recibirLote(List.of(),List.of(estado)).aceptados());assertEquals(1,almacen.recibirLote(List.of(),List.of(estado)).duplicados());
  assertEquals(0,contar("wa_conversacion"));assertEquals(1,contar("wa_evento"));assertNull(almacen.reservar(false));assertNull(almacen.reservar(true));assertEquals(0,contar("wa_respuesta"));
  assertNotNull(jdbc.queryForObject("select contenido_cifrado from wa_evento",byte[].class));
 }
 @Test void conflictoDeEstadoRevierteTambienLosMensajesDelLote(){
  var fecha=Instant.now();var estado=new EstadoEntregaCanal("qa-cuenta","qa-numero","wamid.salida","qa-a","read",fecha);
  almacen.recibirLote(List.of(),List.of(estado));
  assertThrows(AlmacenWhatsAppPort.EventoConflictivo.class,()->almacen.recibirLote(
   List.of(new AlmacenWhatsAppPort.Entrada(mensaje("nuevo","u",MensajeCanal.Tipo.TEXTO,"Error"),fecha)),
   List.of(new EstadoEntregaCanal("qa-cuenta","qa-numero","wamid.salida","qa-b","read",fecha))));
  assertEquals(1,contar("wa_evento"));assertEquals(0,contar("wa_conversacion"));
 }
 @Test void retencionPurgaEstadosSinConversacion(){
  almacen.recibirLote(List.of(),List.of(new EstadoEntregaCanal("qa-cuenta","qa-numero","wamid.salida","qa-a","read",Instant.now())));
  jdbc.update("update wa_evento set recibido_en=CURRENT_TIMESTAMP - INTERVAL '40' DAY,vence_en=CURRENT_TIMESTAMP - INTERVAL '39' DAY,retener_hasta=CURRENT_TIMESTAMP - INTERVAL '1' DAY");
  almacen.recuperar();assertEquals(0,contar("wa_evento"));
 }
 @Test void loteSeRevierteCompleto(){
  var m=mensaje("a","u",MensajeCanal.Tipo.TEXTO,"original");var fecha=Instant.now();almacen.recibir(List.of(new AlmacenWhatsAppPort.Entrada(m,fecha)));
  assertThrows(RuntimeException.class,()->almacen.recibir(List.of(new AlmacenWhatsAppPort.Entrada(mensaje("b","u",MensajeCanal.Tipo.TEXTO,"nuevo"),fecha),new AlmacenWhatsAppPort.Entrada(mensaje("a","u",MensajeCanal.Tipo.TEXTO,"conflicto"),fecha))));assertEquals(1,contar("wa_evento"));
 }
 @Test void reservaExclusivaOrdenYConversacionesIndependientes(){
  recibir(mensaje("a","u",MensajeCanal.Tipo.TEXTO,"a"));recibir(mensaje("b","u",MensajeCanal.Tipo.TEXTO,"b"));
  var a=almacen.reservar(false);assertNotNull(a);assertNull(almacen.reservar(false));
  recibir(mensaje("c","otra",MensajeCanal.Tipo.TEXTO,"c"));var c=almacen.reservar(false);assertNotNull(c);assertNotEquals(a.conversacion(),c.conversacion());
  procesador().ejecutar(a);assertNotNull(almacen.reservar(false));
 }
 @Test void trabajadoresConcurrentesNoReservanDosVeces()throws Exception{
  recibir(mensaje("a","u",MensajeCanal.Tipo.TEXTO,"a"));
  try(var pool=Executors.newFixedThreadPool(2)){
   var inicio=new CountDownLatch(1);var resultados=new ArrayList<Future<AlmacenWhatsAppPort.Reserva>>();
   for(int i=0;i<2;i++)resultados.add(pool.submit(()->{inicio.await();return almacen.reservar(false);}));inicio.countDown();
   int reservas=0;for(var f:resultados)if(f.get(10,TimeUnit.SECONDS)!=null)reservas++;assertEquals(1,reservas);
  }
 }
 @Test void recepcionesConcurrentesCreanUnSoloEvento()throws Exception{
  var e=new AlmacenWhatsAppPort.Entrada(mensaje("a","u",MensajeCanal.Tipo.TEXTO,"Error"),Instant.now());
  try(var pool=Executors.newFixedThreadPool(2)){
   var inicio=new CountDownLatch(1);var a=pool.submit(()->{inicio.await();return almacen.recibir(List.of(e));});var b=pool.submit(()->{inicio.await();return almacen.recibir(List.of(e));});inicio.countDown();
   var primero=a.get(15,TimeUnit.SECONDS);var segundo=b.get(15,TimeUnit.SECONDS);
   assertEquals(1,primero.aceptados()+segundo.aceptados());assertEquals(1,primero.duplicados()+segundo.duplicados());assertEquals(1,contar("wa_conversacion"));assertEquals(1,contar("wa_evento"));
  }
 }
 @Test void correccionRotaReferenciaYConfirmacionSeConsumeUnaVez(){
  recibir(mensaje("a","u",MensajeCanal.Tipo.IMAGEN,"qa-media-legible"));procesador().ejecutar(almacen.reservar(true));
  var primera=almacen.reservarSalida();almacen.simular(primera,primera.respuesta());String anterior=primera.respuesta().referencia();
  recibir(mensaje("b","u",MensajeCanal.Tipo.TEXTO,"CORREGIR "+anterior+" Error E99"));procesador().ejecutar(almacen.reservar(false));
  var segunda=almacen.reservarSalida();String nueva=segunda.respuesta().referencia();assertNotEquals(anterior,nueva);almacen.simular(segunda,segunda.respuesta());
  recibir(mensaje("c","u",MensajeCanal.Tipo.TEXTO,"CONFIRMAR "+anterior));procesador().ejecutar(almacen.reservar(false));
  assertEquals(1,jdbc.queryForObject("select count(*) from wa_evento where resultado_codigo='INVALIDO'",Integer.class));
  recibir(mensaje("d","u",MensajeCanal.Tipo.TEXTO,"CONFIRMAR "+nueva));procesador().ejecutar(almacen.reservar(false));
  recibir(mensaje("e","u",MensajeCanal.Tipo.TEXTO,"CONFIRMAR "+nueva));procesador().ejecutar(almacen.reservar(false));
  assertEquals(1,jdbc.queryForObject("select count(*) from wa_evento where resultado_codigo='RESULTADOS'",Integer.class));
 }
 @Test void cancelacionDurableNoBusca(){
  recibir(mensaje("a","u",MensajeCanal.Tipo.IMAGEN,"qa-media-legible"));procesador().ejecutar(almacen.reservar(true));
  String ref=jdbc.queryForObject("select referencia from wa_conversacion",String.class);
  recibir(mensaje("b","u",MensajeCanal.Tipo.TEXTO,"CANCELAR "+ref));
  new ProcesarReservaWhatsAppService(almacen,q->{fail("Cancelar no debe buscar");return List.of();},m->List.of()).ejecutar(almacen.reservar(false));
  assertEquals("LIBRE",jdbc.queryForObject("select estado from wa_conversacion",String.class));assertNull(jdbc.queryForObject("select estado_cifrado from wa_conversacion",byte[].class));
 }
 @Test void unaRestriccionDeSalidaRevierteLaFinalizacionCompleta(){
  recibir(mensaje("a","u",MensajeCanal.Tipo.TEXTO,"a"));var r=almacen.reservar(false);
  jdbc.update("insert into wa_respuesta(id,evento_id,conversacion_id,estado,creado_en,actualizado_en,vence_en,retener_hasta) values(?,?,?,'CANCELADA',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",UUID.randomUUID().toString(),r.evento(),r.conversacion());
  assertThrows(RuntimeException.class,()->almacen.finalizar(r,null,RespuestaCanal.simple(RespuestaCanal.Estado.CANCELADO,"controlado")));
  assertEquals("EN_PROCESO",jdbc.queryForObject("select estado from wa_evento",String.class));assertEquals(r.token(),jdbc.queryForObject("select reserva_token from wa_conversacion",String.class));assertEquals(1,contar("wa_respuesta"));
 }
 @Test void recuperacionRechazaTrabajadorAnterior(){
  recibir(mensaje("a","u",MensajeCanal.Tipo.TEXTO,"a"));var viejo=almacen.reservar(false);
  jdbc.update("update wa_conversacion set reserva_hasta=CURRENT_TIMESTAMP - INTERVAL '1' SECOND");jdbc.update("update wa_evento set reserva_hasta=CURRENT_TIMESTAMP - INTERVAL '1' SECOND");
  almacen.recuperar();jdbc.update("update wa_evento set proximo_intento=CURRENT_TIMESTAMP");var nuevo=almacen.reservar(false);
  assertNotNull(nuevo);assertTrue(nuevo.cercado()>viejo.cercado());assertFalse(almacen.finalizar(viejo,null,RespuestaCanal.simple(RespuestaCanal.Estado.CANCELADO,"antiguo")));
  procesador().ejecutar(nuevo);assertEquals(1,contar("wa_respuesta"));
 }
 @Test void reintentosAcotadosNoCreanSalidaPrematura(){
  recibir(mensaje("a","u",MensajeCanal.Tipo.TEXTO,"a"));
  var falla=new ProcesarReservaWhatsAppService(almacen,q->{throw new IllegalStateException("dato sensible");},m->List.of());
  for(int i=0;i<5;i++){falla.ejecutar(almacen.reservar(false));jdbc.update("update wa_evento set proximo_intento=CURRENT_TIMESTAMP");}
  assertEquals("ERROR_PERMANENTE",jdbc.queryForObject("select estado from wa_evento",String.class));assertEquals(0,contar("wa_respuesta"));
 }
 @Test void imagenPosteriorDescartaOcrAnterior(){
  recibir(mensaje("a","u",MensajeCanal.Tipo.IMAGEN,"qa-media-legible"));var antiguo=almacen.reservar(true);
  recibir(mensaje("b","u",MensajeCanal.Tipo.IMAGEN,"qa-media-vacia"));procesador().ejecutar(antiguo);
  assertEquals(0,contar("wa_respuesta"));assertEquals("SUPERADO",jdbc.queryForObject("select estado from wa_evento where id=?",String.class,antiguo.evento()));assertNotNull(almacen.reservar(true));
 }
 @Test void preparacionSeRecuperaPeroEnvioAmbiguoNoSeRepite(){
  recibir(mensaje("a","u",MensajeCanal.Tipo.TEXTO,"a"));procesador().ejecutar(almacen.reservar(false));var s=almacen.reservarSalida();
  jdbc.update("update wa_respuesta set reserva_hasta=CURRENT_TIMESTAMP - INTERVAL '1' SECOND");almacen.recuperar();
  assertFalse(almacen.simular(s,s.respuesta()));jdbc.update("update wa_respuesta set proximo_intento=CURRENT_TIMESTAMP");assertNotNull(almacen.reservarSalida());
  jdbc.update("update wa_respuesta set estado='ENVIANDO',preparado_cifrado=plan_cifrado,reserva_hasta=CURRENT_TIMESTAMP - INTERVAL '1' SECOND");almacen.recuperar();
  assertEquals("INCIERTA",jdbc.queryForObject("select estado from wa_respuesta",String.class));assertNull(almacen.reservarSalida());
 }
 @Test void cifradoAutenticadoNoPermiteCambiarContexto(){
  var m=mensaje("a","u",MensajeCanal.Tipo.TEXTO,"secreto");var b=crypto.proteger(m,"evento:a");
  assertFalse(new String(b,java.nio.charset.StandardCharsets.UTF_8).contains("secreto"));assertEquals(m,crypto.leer(b,MensajeCanal.class,"evento:a"));
  assertThrows(IllegalStateException.class,()->crypto.leer(b,MensajeCanal.class,"evento:b"));b[b.length-1]^=1;
  assertThrows(IllegalStateException.class,()->crypto.leer(b,MensajeCanal.class,"evento:a"));
 }
 @Test void eventoCorruptoTieneReintentosAcotados(){
  recibir(mensaje("a","u",MensajeCanal.Tipo.TEXTO,"a"));jdbc.update("update wa_evento set contenido_cifrado=?",new byte[30]);
  for(int i=0;i<5;i++){assertNull(almacen.reservar(false));jdbc.update("update wa_evento set proximo_intento=CURRENT_TIMESTAMP");}
  assertEquals("ERROR_PERMANENTE",jdbc.queryForObject("select estado from wa_evento",String.class));assertNull(jdbc.queryForObject("select reserva_token from wa_conversacion",String.class));
  recibir(mensaje("b","u",MensajeCanal.Tipo.TEXTO,"b"));assertNotNull(almacen.reservar(false));
 }
 @Test void planCorruptoNoBloqueaLaSalidaIndefinidamente(){
  recibir(mensaje("a","u",MensajeCanal.Tipo.TEXTO,"a"));procesador().ejecutar(almacen.reservar(false));jdbc.update("update wa_respuesta set plan_cifrado=?",new byte[30]);
  for(int i=0;i<5;i++){assertNull(almacen.reservarSalida());jdbc.update("update wa_respuesta set proximo_intento=CURRENT_TIMESTAMP");}
  assertEquals("ERROR_PERMANENTE",jdbc.queryForObject("select estado from wa_respuesta",String.class));assertNull(jdbc.queryForObject("select plan_cifrado from wa_respuesta",byte[].class));
 }
 @Test void httpAutenticaAntesDePersistirYAceptaSinEjecutarOcr()throws Exception{
  String secreto="secreto-qa-sintetico-largo";var validacion=new ValidacionWebhookLocal("token-qa-sintetico-largo",secreto);var entrada=new EntradaWebhookHttpAdapter(almacen,validacion);
  byte[] cuerpo=new String(WebhookSimuladoTest.evento("wamid.qa-a","qa-usuario-a","image","qa-media-legible"),java.nio.charset.StandardCharsets.UTF_8).replace("1794560400",Long.toString(Instant.now().getEpochSecond())).getBytes(java.nio.charset.StandardCharsets.UTF_8);
  assertThrows(ValidacionWebhookLocal.Rechazo.class,()->entrada.recibir(cuerpo,"sha256="+"0".repeat(64)));assertEquals(0,contar("wa_evento"));
  Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secreto.getBytes(),"HmacSHA256"));String firma="sha256="+HexFormat.of().formatHex(mac.doFinal(cuerpo));
  var ack=entrada.recibir(cuerpo,firma);assertEquals(1,ack.aceptados());assertTrue(ack.respuestas().isEmpty());assertEquals("PENDIENTE",jdbc.queryForObject("select estado from wa_evento",String.class));
  assertEquals(1,entrada.recibir(cuerpo,firma).duplicados());
 }
 @Test @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="WA_QA_REINICIO",matches="crear")
 void reinicioCrear(){
  jdbc.update("delete from wa_respuesta");jdbc.update("delete from wa_evento");jdbc.update("delete from wa_conversacion");
  recibir(mensaje("reinicio","u",MensajeCanal.Tipo.IMAGEN,"qa-media-legible"));assertNotNull(almacen.reservar(true));
  jdbc.update("update wa_conversacion set reserva_hasta=CURRENT_TIMESTAMP - INTERVAL '1' SECOND");
  jdbc.update("update wa_evento set reserva_hasta=CURRENT_TIMESTAMP - INTERVAL '1' SECOND");
 }
 @Test @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="WA_QA_REINICIO",matches="recuperar")
 void reinicioRecuperar(){
  assertEquals(1,contar("wa_evento"));assertEquals("EN_PROCESO",jdbc.queryForObject("select estado from wa_evento",String.class));
  almacen.recuperar();jdbc.update("update wa_evento set proximo_intento=CURRENT_TIMESTAMP");var r=almacen.reservar(true);
  assertNotNull(r);assertEquals(2,r.cercado());procesador().ejecutar(r);assertEquals(1,contar("wa_respuesta"));
  var s=almacen.reservarSalida();assertNotNull(s);assertEquals(RespuestaCanal.Estado.CONFIRMACION,s.respuesta().estado());assertTrue(almacen.simular(s,s.respuesta()));
 }
}
