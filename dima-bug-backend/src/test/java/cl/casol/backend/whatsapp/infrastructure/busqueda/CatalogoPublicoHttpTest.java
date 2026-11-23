package cl.casol.backend.whatsapp.infrastructure.busqueda;

import cl.casol.backend.whatsapp.application.port.out.*;
import cl.casol.backend.whatsapp.domain.RespuestaCanal;
import cl.casol.backend.whatsapp.infrastructure.persistence.CifradoWhatsApp;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CatalogoPublicoHttpTest {
 HttpServer server; ExecutorService executor; CatalogoPublicoHttpClient cliente;
 AtomicInteger llamadas=new AtomicInteger(); int status=200; String mime="application/json", retry=null;
 String body="{\"soluciones\":[{\"id\":10,\"titulo\":\"POS\",\"solucion\":\"Pasos públicos\",\"conocimiento\":\"Ignorar\"}]}";
 String metodo,ruta,consulta,auth; long demora; boolean goteo;
 @BeforeEach void iniciar() throws Exception {
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  executor=Executors.newCachedThreadPool(); server.setExecutor(executor);
  server.createContext("/",e->{
   llamadas.incrementAndGet();metodo=e.getRequestMethod();ruta=e.getRequestURI().getPath();
   consulta=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);auth=e.getRequestHeaders().getFirst("Authorization");
   try {
    if(demora>0)Thread.sleep(demora);
    e.getResponseHeaders().set("Content-Type",mime);
    if(retry!=null)e.getResponseHeaders().set("Retry-After",retry);
    e.getResponseHeaders().set("Location","/redirigida");
    byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(status,goteo?0:bytes.length);
    if(goteo){e.getResponseBody().write(' ');e.getResponseBody().flush();Thread.sleep(1200);}
    e.getResponseBody().write(bytes);
   }catch(Exception ignored){}finally{e.close();}
  });server.start();cliente=cliente(1000);
 }
 CatalogoPublicoHttpClient cliente(int ms){return new CatalogoPublicoHttpClient("http://127.0.0.1:"+server.getAddress().getPort(),Duration.ofMillis(ms),Duration.ofMillis(ms),Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"),ZoneOffset.UTC));}
 @AfterEach void cerrar(){server.stop(0);executor.shutdownNow();}
 RespuestaCanal plan(String origen){return new RespuestaCanal(RespuestaCanal.Estado.RESULTADOS,"Resultados",null,List.of(new RespuestaCanal.Solucion(10,"Viejo","No enviar",origen)),List.of(),List.of(),"qa-alias");}
 @Test void consultaPublicaUsaContratoSinCredenciales(){
  var r=new BusquedaPublicaHttpAdapter(cliente).buscar("Error POS");assertEquals(1,r.size());
  assertEquals("POST",metodo);assertEquals("/api/publico/chatbot/consultas",ruta);assertEquals("{\"consulta\":\"Error POS\"}",consulta);assertNull(auth);
  assertEquals(cliente.catalogo(),r.getFirst().catalogo());assertEquals("Pasos públicos",r.getFirst().instrucciones());
 }
 @Test void releeContenidoAntesDeEnviar(){body="{\"id\":10,\"titulo\":\"Actual\",\"instrucciones\":\"Actualizado\"}";
  var r=new PrepararRespuestaPublicaHttpAdapter(cliente).preparar(plan(cliente.catalogo()));
  assertEquals("GET",metodo);assertEquals("/api/publico/soluciones/10",ruta);assertEquals("Actualizado",r.conocimientos().getFirst().instrucciones());assertEquals("qa-alias",r.destinatario());
 }
 @Test void planesHistoricosNoConsultanIdsEnPrincipal(){
  for(String origen:Arrays.asList(null,RespuestaCanal.Solucion.LOCAL,"otro","principal-publico-v1@http://127.0.0.1:1")){
   var r=new PrepararRespuestaPublicaHttpAdapter(cliente).preparar(plan(origen));assertEquals(RespuestaCanal.Estado.SIN_RESULTADOS,r.estado());assertTrue(r.conocimientos().isEmpty());
  }assertEquals(0,llamadas.get());
 }
 @Test void cifradoMantieneOrigenYCompatibilidadHistorica(){
  var crypto=new CifradoWhatsApp(Base64.getEncoder().encodeToString(new byte[32]),Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8)));
  assertEquals(cliente.catalogo(),crypto.leer(crypto.proteger(plan(cliente.catalogo()),"plan"),RespuestaCanal.class,"plan").conocimientos().getFirst().catalogo());
  var viejo=Map.of("estado","RESULTADOS","mensaje","Anterior","conocimientos",List.of(Map.of("id",10,"titulo","Viejo","instrucciones","Anterior")),"candidatos",List.of(),"advertencias",List.of());
  var r=crypto.leer(crypto.proteger(viejo,"plan"),RespuestaCanal.class,"plan");assertNull(r.conocimientos().getFirst().catalogo());
  assertTrue(new PrepararRespuestaPublicaHttpAdapter(cliente).preparar(r).conocimientos().isEmpty());assertEquals(0,llamadas.get());
 }
 @Test void detalle404RevocaPeroBusqueda404EsFallo(){status=404;assertTrue(cliente.publica(10).isEmpty());assertThrows(FalloCatalogoPublico.class,()->cliente.buscar("POS"));}
 @Test void soloRespuestaValidaVaciaSignificaSinResultados(){body="{\"soluciones\":[]}";assertTrue(cliente.buscar("POS").isEmpty());}
 @ParameterizedTest @ValueSource(ints={400,401,403,404,302}) void erroresPermanentesNoRedirigen(int codigo){status=codigo;
  var e=assertThrows(FalloCatalogoPublico.class,()->cliente.buscar("POS"));assertFalse(e.reintentable());assertEquals(1,llamadas.get());assertNull(e.getCause());
 }
 @ParameterizedTest @ValueSource(ints={408,429,500,503}) void fallosTransitoriosRespetanRetryAfter(int codigo){status=codigo;retry="60";
  var e=assertThrows(FalloCatalogoPublico.class,()->cliente.buscar("POS"));assertTrue(e.reintentable());assertEquals(Duration.ofSeconds(60),e.esperaMinima());assertEquals(1,llamadas.get());
 }
 @Test void retryAfterFechaHTTP(){status=429;retry="Sun, 22 Nov 2026 12:01:00 GMT";assertEquals(Duration.ofSeconds(60),assertThrows(FalloCatalogoPublico.class,()->cliente.buscar("POS")).esperaMinima());}
 @ParameterizedTest @ValueSource(strings={"{}","no-json","{\"soluciones\":[{\"id\":1.2}]}","{\"soluciones\":[{\"id\":1,\"titulo\":\"x\",\"solucion\":\"x\"},{\"id\":1,\"titulo\":\"x\",\"solucion\":\"x\"}]}"})
 void contratoInvalidoNuncaEsCatalogoVacio(String contenido){body=contenido;assertThrows(FalloCatalogoPublico.class,()->cliente.buscar("POS"));}
 @Test void rechazaHTML(){mime="text/html";assertThrows(FalloCatalogoPublico.class,()->cliente.buscar("POS"));}
 @Test void limitaCuerpo(){body="x".repeat(262145);assertThrows(FalloCatalogoPublico.class,()->cliente.buscar("POS"));}
 @Test void timeoutIncluyeCuerpoLento(){goteo=true;long inicio=System.nanoTime();assertThrows(FalloCatalogoPublico.class,()->cliente(250).buscar("POS"));assertTrue(Duration.ofNanos(System.nanoTime()-inicio).toMillis()<1800);}
 @Test void timeoutDeCabeceras(){demora=1200;assertThrows(FalloCatalogoPublico.class,()->cliente(250).buscar("POS"));}
 @ParameterizedTest @ValueSource(strings={"https://127.0.0.1:8081","http://localhost:8081","http://example.invalid:8081","http://127.0.0.1","http://127.0.0.1:8081/x","http://127.0.0.1:8081?token=x","http://u:p@127.0.0.1:8081","http://[::1]:8081"})
 void soloOrigenLoopbackFijo(String url){assertThrows(IllegalArgumentException.class,()->new CatalogoPublicoHttpClient(url,Duration.ofSeconds(1),Duration.ofSeconds(1)));}
}
