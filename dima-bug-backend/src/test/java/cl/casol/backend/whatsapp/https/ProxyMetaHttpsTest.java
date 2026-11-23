package cl.casol.backend.whatsapp.https;

import cl.casol.backend.whatsapp.infrastructure.meta.*;
import cl.casol.backend.whatsapp.infrastructure.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import tools.jackson.databind.json.JsonMapper;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** TCP/TLS reales, proxy de QA y MySQL exclusivo; solo se ejecuta con su lanzador. */
@EnabledIfEnvironmentVariable(named="WA_QA_HTTPS_RUN",matches=".+")
@ActiveProfiles({"qa-whatsapp-meta","qa-whatsapp-https-test"})
@SpringBootTest(classes=ProxyMetaHttpsTest.Contexto.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"spring.sql.init.mode=never","spring.jpa.hibernate.ddl-auto=validate",
 "spring.jpa.mapping-resources=META-INF/whatsapp.orm.xml","spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
 "spring.security.user.name=qa_sin_acceso","spring.security.user.password=sintetico-sin-uso-qa",
 "server.tomcat.accesslog.enabled=false","spring.mvc.log-request-details=false",
 "dimabug.whatsapp.meta-qa.waba=qa-waba-32b","dimabug.whatsapp.meta-qa.numero=qa-numero-32b",
 "dimabug.whatsapp.meta-qa.participantes=tel:100000000000001=qa-a,bsuid:US.qa32b=qa-a",
 "dimabug.whatsapp.qa.raiz=.."})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProxyMetaHttpsTest {
    static final String TOKEN=secreto(),SECRET=secreto(),ACCESS=secreto();
    static final JsonMapper JSON=JsonMapper.builder().build();
    static String secreto(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return HexFormat.of().formatHex(b);}
    @Configuration(proxyBeanMethods=false) @Profile("qa-whatsapp-https-test") @EnableAutoConfiguration
    @EntityScan(basePackageClasses=ConversacionWhatsAppEntity.class)
    @EnableJpaRepositories(basePackageClasses=ConversacionWhatsAppRepository.class)
    @Import({AlmacenWhatsAppJpaAdapter.class,MetaQaConfig.class,WebhookMetaQaController.class,WhatsAppDurablePerfilGuard.class})
    static class Contexto {
        @Bean CifradoWhatsApp cifrado(){return new CifradoWhatsApp(Base64.getEncoder().encodeToString(HexFormat.of().parseHex(secreto())),Base64.getEncoder().encodeToString(HexFormat.of().parseHex(secreto())));}
    }
    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r){
        String url=System.getenv("WA_QA_JDBC_URL");
        if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/wa_durable_[a-f0-9]+\\?.*"))throw new IllegalStateException("MySQL exclusivo no disponible");
        r.add("spring.datasource.url",()->url);r.add("spring.datasource.username",()->"wa_worker");
        r.add("spring.datasource.password",()->System.getenv("WA_QA_JDBC_PASSWORD"));
        r.add("dimabug.whatsapp.meta-qa.verify-token",()->TOKEN);r.add("dimabug.whatsapp.meta-qa.app-secret",()->SECRET);
    }
    @Value("${local.server.port}") int backend;
    @Autowired ControlesMetaQa controles;
    @Autowired JdbcTemplate jdbc;
    ProxyHttpsLocal proxy;
    HttpClient cliente;
    Path carpeta;
    final Set<String> firmas=ConcurrentHashMap.newKeySet();
    ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logsBackend;
    @BeforeAll void iniciar()throws Exception{
        carpeta=Path.of(System.getenv("WA_QA_HTTPS_RUN")).toAbsolutePath();
        proxy=new ProxyHttpsLocal(carpeta,backend);
        cliente=HttpClient.newBuilder().sslContext(proxy.clienteTls).connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
        logsBackend=new ch.qos.logback.core.read.ListAppender<>();logsBackend.start();
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(logsBackend);
        assertEquals(new ControlesMetaQa.Estado(false,false,false,false),controles.instantanea());
        Files.writeString(carpeta.resolve("listeners.json"),JSON.writeValueAsString(Map.of("proxy_bind","127.0.0.1","proxy_port",proxy.puerto(),"spring_bind","127.0.0.1","spring_port",backend,"tls","certificado localhost autofirmado; no certificado público")));
    }
    @AfterAll void cerrar()throws Exception{
        try{
            if(proxy!=null){
                String logs=logsBackend.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).collect(java.util.stream.Collectors.joining("\n"));
                Files.writeString(carpeta.resolve("backend-capturado.log"),logs);
                String todos=logs+proxy.registros();
                for(String s:List.of(TOKEN,SECRET,ACCESS,"100000000000001","MARCADOR_PRIVADO_ID","MARCADOR_PRIVADO_TEXTO","hub.verify_token="))assertFalse(todos.contains(s));
                for(String f:firmas)assertFalse(todos.contains(f));
                Files.writeString(carpeta.resolve("proxy.log"),proxy.registros());
                Files.writeString(carpeta.resolve("privacidad-final.json"),JSON.writeValueAsString(Map.of("proxy_y_backend_matches",0,"firmas_comprobadas",firmas.size(),"query_logging",false)));
            }
        }finally{
            if(logsBackend!=null){((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(logsBackend);logsBackend.stop();}
            if(proxy!=null)proxy.close();if(cliente!=null)cliente.close();
        }
    }
    @BeforeEach void limpiar(){controles.recepcion(false);controles.procesamiento(false);controles.medios(false);controles.envio(false);jdbc.update("delete from wa_respuesta");jdbc.update("delete from wa_evento");jdbc.update("delete from wa_conversacion");}
    byte[] evento(String id,String tel,String mensaje){return JSON.writeValueAsBytes(Map.of("object","whatsapp_business_account","entry",List.of(Map.of("id","qa-waba-32b","changes",List.of(Map.of("field","messages","value",Map.of("messaging_product","whatsapp","metadata",Map.of("phone_number_id","qa-numero-32b"),"messages",List.of(Map.of("id",id,"from",tel,"timestamp",Long.toString(Instant.now().getEpochSecond()),"type","text","text",Map.of("body",mensaje))))))))));}
    String firma(byte[] b)throws Exception{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));String f="sha256="+HexFormat.of().formatHex(m.doFinal(b));firmas.add(f);return f;}
    HttpResponse<byte[]> request(String metodo,String ruta,byte[] b,String sign)throws Exception{
        var r=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+proxy.puerto()+ruta)).timeout(Duration.ofSeconds(30))
            .header("Authorization","Bearer "+ACCESS).header("Forwarded","for=192.0.2.123").header("X-Forwarded-For","192.0.2.123")
            .method(metodo,b==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(b));
        if(b!=null)r.header("Content-Type","application/json");if(sign!=null)r.header("X-Hub-Signature-256",sign);
        return cliente.send(r.build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    int contar(){return jdbc.queryForObject("select count(*) from wa_evento",Integer.class);}
    @Test void getExactoSinRedireccion()throws Exception{
        var r=request("GET","/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token="+TOKEN+"&hub.challenge=000123",null,null);
        assertEquals(200,r.statusCode());assertArrayEquals("000123".getBytes(),r.body());assertTrue(r.headers().firstValue("Location").isEmpty());
        assertTrue(Set.of("TLSv1.3","TLSv1.2").contains(r.sslSession().orElseThrow().getProtocol()));
    }
    @Test void tokenIncorrectoEsRechazado()throws Exception{assertEquals(403,request("GET","/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=incorrecto&hub.challenge=x",null,null).statusCode());}
    @Test void metodosYRutasNoLleganAlBackend()throws Exception{
        for(String m:List.of("PUT","DELETE","PATCH","HEAD","OPTIONS"))assertEquals(403,request(m,"/webhooks/whatsapp",null,null).statusCode());
        for(String p:List.of("/","/api","/api/publico/chatbot/consultas","/actuator","/actuator/health","/swagger-ui/index.html","/v3/api-docs","/webhooks/whatsapp/","/webhooks/%77hatsapp","//webhooks/whatsapp")){
            var r=request("GET",p,null,null);
            if(p.startsWith("//"))assertTrue(Set.of(400,403).contains(r.statusCode()));
            else{assertEquals(403,r.statusCode(),"Ruta QA rechazada");assertEquals("RECHAZADO_PROXY",new String(r.body()));}
        }assertEquals(0,contar());
    }
    @Test void postConfirmaCommitYConservaEventoSinProcesarlo()throws Exception{
        controles.recepcion(true);byte[] b=evento("wamid.commit","100000000000001","Error acentuado á ñ");
        var r=request("POST","/webhooks/whatsapp",b,firma(b));assertEquals(200,r.statusCode());
        // Conexión independiente posterior a la respuesta: la fila ya está comprometida.
        assertEquals(1,contar());assertEquals("PENDIENTE",jdbc.queryForObject("select estado from wa_evento",String.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from wa_respuesta",Integer.class));
        assertEquals(1,JSON.readTree(request("POST","/webhooks/whatsapp",b,firma(b)).body()).path("duplicados").asInt());assertEquals(1,contar());
    }
    @Test void firmaConservaEspaciosYUnicodePeroRechazaAlteraciones()throws Exception{
        controles.recepcion(true);byte[] original=evento("wamid.bytes","100000000000001","Error á");
        byte[] b=(" \n"+new String(original,StandardCharsets.UTF_8)+"\n ").getBytes(StandardCharsets.UTF_8);
        assertEquals(200,request("POST","/webhooks/whatsapp",b,firma(b)).statusCode());
        byte[] alterado=Arrays.copyOf(b,b.length+1);alterado[alterado.length-1]=' ';
        assertEquals(403,request("POST","/webhooks/whatsapp",alterado,firma(b)).statusCode());
        assertEquals(403,request("POST","/webhooks/whatsapp",original,"sha256="+"0".repeat(64)).statusCode());assertEquals(1,contar());
    }
    @Test void alcanceNoAutorizadoNoPersiste()throws Exception{
        controles.recepcion(true);byte[] b=evento("wamid.noautorizado","100000000000099","texto");assertEquals(403,request("POST","/webhooks/whatsapp",b,firma(b)).statusCode());assertEquals(0,contar());
    }
    @Test void cuerpoSuperiorAlLimiteSeRechaza()throws Exception{
        controles.recepcion(true);byte[] b=new byte[ProxyHttpsLocal.MAX+1];assertEquals(413,request("POST","/webhooks/whatsapp",b,firma(b)).statusCode());assertEquals(0,contar());
    }
    @Test void recepcionPausadaNoDaAckFalso()throws Exception{
        byte[] b=evento("wamid.pausa","100000000000001","Error");assertEquals(503,request("POST","/webhooks/whatsapp",b,firma(b)).statusCode());assertEquals(0,contar());
    }
    @Test void duplicadosConcurrentesGeneranUnaSolaFila()throws Exception{
        controles.recepcion(true);byte[] b=evento("wamid.concurrente","100000000000001","Error");String f=firma(b);
        try(var pool=Executors.newFixedThreadPool(4)){
            var futuros=new ArrayList<Future<Integer>>();for(int i=0;i<4;i++)futuros.add(pool.submit(()->request("POST","/webhooks/whatsapp",b,f).statusCode()));
            for(var futuro:futuros)assertEquals(200,futuro.get(30,TimeUnit.SECONDS));
        }assertEquals(1,contar());
    }
    @Test void indisponibilidadMySqlNoProduce200()throws Exception{
        controles.recepcion(true);
        Files.writeString(carpeta.resolve("solicitar-parada-mysql"),"QA");esperarArchivo("mysql-detenido");
        try{byte[] b=evento("wamid.bd","100000000000001","Error");assertEquals(503,request("POST","/webhooks/whatsapp",b,firma(b)).statusCode());}
        finally{Files.writeString(carpeta.resolve("solicitar-reinicio-mysql"),"QA");esperarArchivo("mysql-reiniciado");}
        ((com.zaxxer.hikari.HikariDataSource)jdbc.getDataSource()).getHikariPoolMXBean().softEvictConnections();
        long fin=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);Integer filas=null;
        while(System.nanoTime()<fin&&filas==null)try{filas=contar();}catch(org.springframework.dao.DataAccessException e){Thread.sleep(200);}
        assertEquals(0,filas);
    }
    private void esperarArchivo(String nombre)throws Exception{
        long fin=System.nanoTime()+TimeUnit.SECONDS.toNanos(70);
        while(!Files.exists(carpeta.resolve(nombre))&&System.nanoTime()<fin)Thread.sleep(100);
        assertTrue(Files.exists(carpeta.resolve(nombre)),"Coordinación de MySQL exclusivo no disponible");
    }
    @Test void logsNoContienenSecretosNiDatos()throws Exception{
        controles.recepcion(true);byte[] b=evento("MARCADOR_PRIVADO_ID","100000000000001","MARCADOR_PRIVADO_TEXTO");request("POST","/webhooks/whatsapp",b,firma(b));
        String logs=proxy.registros();for(String s:List.of(TOKEN,SECRET,ACCESS,"100000000000001","MARCADOR_PRIVADO_ID","MARCADOR_PRIVADO_TEXTO","hub.verify_token","Bearer"))assertFalse(logs.contains(s));
        for(String f:firmas)assertFalse(logs.contains(f));
        Files.writeString(carpeta.resolve("privacidad.json"),JSON.writeValueAsString(Map.of("proxy_matches",0,"raw_request_logging",false)));
    }
}
