import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.net.ssl.*;

/** HTTPS real con confianza explícita en certificado sintético; nunca trust-all. */
public class HttpsProbe {
    static final String HOST="wa-qa.dimabug.casol.cl",BASE="https://127.0.0.1:18443";
    static Properties properties=new Properties();
    static HttpClient client;
    record Result(int status,String body,long millis){}
    static void check(boolean ok,String error){if(!ok)throw new IllegalStateException(error);}
    static String signature(byte[] body)throws Exception{
        Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(properties.getProperty("dimabug.whatsapp.meta-qa.app-secret").getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return "sha256="+HexFormat.of().formatHex(m.doFinal(body));
    }
    static byte[] event(String id,boolean padded){
        String value="{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":\"qa-waba-local\",\"changes\":[{\"field\":\"messages\",\"value\":{\"messaging_product\":\"whatsapp\",\"metadata\":{\"phone_number_id\":\"qa-numero-local\"},\"messages\":[{\"id\":\""+id+"\",\"from\":\"100000000000001\",\"timestamp\":\""+properties.getProperty("qa.probe.timestamp")+"\",\"type\":\"text\",\"text\":{\"body\":\"PRIVADO_C4_áéí_ñ\"}}]}}]}]}";
        return ((padded?" \r\n"+" ".repeat(80*1024):"")+value+"\r\n").getBytes(StandardCharsets.UTF_8);
    }
    static Result send(String method,String path,byte[] body,String signed)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(BASE+path)).timeout(java.time.Duration.ofSeconds(20)).header("Host",HOST);
        if(body!=null)b.header("Content-Type","application/json");
        if(signed!=null)b.header("X-Hub-Signature-256",signed);
        b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));
        long start=System.nanoTime();var r=client.send(b.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        check(r.headers().firstValue("X-WA-QA-Gateway").orElse("").equals("nginx"),"Respuesta no paso por Nginx");
        check(r.sslSession().isPresent(),"TLS ausente");
        return new Result(r.statusCode(),r.body(),(System.nanoTime()-start)/1_000_000);
    }
    static boolean has(Result r,String key,int n){return r.body.matches("(?s).*\\\""+key+"\\\"\\s*:\\s*"+n+"(?:[,}]).*");}
    static void pause()throws Exception{Thread.sleep(650);}
    public static void main(String[] args)throws Exception{
        System.setProperty("jdk.httpclient.allowRestrictedHeaders","host");
        try(var in=Files.newInputStream(Path.of("/qa/application.properties"))){properties.load(in);}
        var cert=CertificateFactory.getInstance("X.509").generateCertificate(Files.newInputStream(Path.of("/qa/cert.pem")));
        KeyStore trust=KeyStore.getInstance(KeyStore.getDefaultType());trust.load(null,null);trust.setCertificateEntry("synthetic",cert);
        TrustManagerFactory tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(trust);
        SSLContext context=SSLContext.getInstance("TLS");context.init(null,tm.getTrustManagers(),null);
        client=HttpClient.newBuilder().sslContext(context).version(HttpClient.Version.HTTP_1_1).connectTimeout(java.time.Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
        String mode=args[0];
        if(mode.equals("cases")){
            boolean untrusted=false,mismatch=false;
            try{HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(BASE)).build(),HttpResponse.BodyHandlers.discarding());}
            catch(SSLHandshakeException e){untrusted=true;}
            try{client.send(HttpRequest.newBuilder(URI.create("https://localhost:18443")).build(),HttpResponse.BodyHandlers.discarding());}
            catch(SSLHandshakeException e){mismatch=true;}
            check(untrusted&&mismatch,"Validacion TLS negativa fallo");
            String query="/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token="+properties.getProperty("dimabug.whatsapp.meta-qa.verify-token")+"&hub.challenge=000123";
            Result get=send("GET",query,null,null);check(get.status==200&&get.body.equals("000123"),"Challenge alterado");pause();
            byte[] body=event("qa-c4-cold",true);String signed=signature(body);
            // Primer POST del proceso Spring: antes de cualquier calentamiento SQL.
            Result cold=send("POST","/webhooks/whatsapp",body,signed);check(cold.status==200&&has(cold,"aceptados",1),"POST frio rechazo");pause();
            byte[] changed=body.clone();changed[0]='\n';
            check(send("POST","/webhooks/whatsapp",changed,signed).status==403,"Byte alterado aceptado");pause();
            String bad=signed.substring(0,signed.length()-1)+(signed.endsWith("0")?"1":"0");
            check(send("POST","/webhooks/whatsapp",body,bad).status==403,"Firma alterada aceptada");pause();
            check(send("POST","/webhooks/whatsapp",body,null).status==403,"Firma ausente aceptada");pause();
            for(String route:List.of("/api/publico/chatbot/consultas","/actuator/health","/swagger-ui/index.html","/webhooks/%77hatsapp")){
                check(send("GET",route,null,null).status==403,"Ruta interna expuesta");pause();
            }
            check(send("PUT","/webhooks/whatsapp",null,null).status==403,"Metodo extra permitido");
            Thread.sleep(2000);
            byte[] duplicate=event("qa-c4-duplicate",false);String ds=signature(duplicate);
            var pool=Executors.newFixedThreadPool(4);var futures=new ArrayList<Future<Result>>();
            int accepted=0,duplicates=0;
            try{
                for(int i=0;i<4;i++)futures.add(pool.submit(()->send("POST","/webhooks/whatsapp",duplicate,ds)));
                for(var f:futures){Result r=f.get();check(r.status==200,"Duplicado fallo");if(has(r,"aceptados",1))accepted++;if(has(r,"duplicados",1))duplicates++;}
            }finally{pool.shutdownNow();}
            check(accepted==1&&duplicates==3,"Deduplicacion incorrecta");
            System.out.println("{\"https\":true,\"untrusted_rejected\":true,\"hostname_mismatch_rejected\":true,\"challenge_exact\":true,\"cold_post_ms\":"+cold.millis+",\"signed_body_bytes\":"+body.length+",\"tampered_body_status\":403,\"tampered_signature_status\":403,\"missing_signature_status\":403,\"internal_routes_blocked\":true,\"concurrent_accepted\":1,\"concurrent_duplicates\":3}");
        }else{
            byte[] body=event("qa-c4-sql-retry",false);String s=signature(body);
            Result r=send("POST","/webhooks/whatsapp",body,s);
            if(mode.equals("fault")){
                check(r.status==503&&r.body.replaceAll("\\s","").equals("{\"estado\":\"RECHAZADO\"}"),"SQL fallo sin respuesta controlada");
                System.out.println("{\"status\":503,\"controlled_body\":true,\"elapsed_ms\":"+r.millis+"}");
            }else if(mode.equals("retry")){
                check(r.status==200&&has(r,"aceptados",1),"Reintento no aceptado");pause();
                Result again=send("POST","/webhooks/whatsapp",body,s);check(again.status==200&&has(again,"duplicados",1),"Reintento duplicado incorrecto");
                System.out.println("{\"retry_accepted\":1,\"repeat_duplicate\":1,\"elapsed_ms\":"+r.millis+"}");
            }else throw new IllegalArgumentException("Modo no permitido");
        }
        client.close();
    }
}
