import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/** Cliente sintético separado del cgroup Spring. No imprime payloads ni secretos. */
public class LoadProbe {
    static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build();
    static String secret;
    static int backendPort;
    static long post(String id,int participante)throws Exception{
        String body="{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":\"qa-waba-local\",\"changes\":[{\"field\":\"messages\",\"value\":{\"messaging_product\":\"whatsapp\",\"metadata\":{\"phone_number_id\":\"qa-numero-local\"},\"messages\":[{\"id\":\""+id+"\",\"from\":\"10000000000000"+participante+"\",\"timestamp\":\""+java.time.Instant.now().getEpochSecond()+"\",\"type\":\"text\",\"text\":{\"body\":\"SINTETICO_C3\"}}]}}]}]}";
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+backendPort+"/webhooks/whatsapp"))
            .timeout(java.time.Duration.ofSeconds(10)).header("Content-Type","application/json")
            .header("X-Hub-Signature-256","sha256="+HexFormat.of().formatHex(mac.doFinal(bytes))).POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        long start=System.nanoTime();var r=CLIENT.send(request,HttpResponse.BodyHandlers.discarding());
        if(r.statusCode()!=200)throw new IllegalStateException("HTTP "+r.statusCode());
        return (System.nanoTime()-start)/1_000_000;
    }
    public static void main(String[] args)throws Exception{
        Properties p=new Properties();try(var in=Files.newInputStream(Path.of("/qa/application.properties"))){p.load(in);}
        secret=p.getProperty("dimabug.whatsapp.meta-qa.app-secret");
        backendPort=Integer.parseInt(p.getProperty("server.port","8082"));
        if(args[0].equals("ready")){
            String token=p.getProperty("dimabug.whatsapp.meta-qa.verify-token");
            long deadline=System.nanoTime()+java.time.Duration.ofSeconds(90).toNanos();
            while(System.nanoTime()<deadline){
                try{var r=CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+backendPort+"/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token="+token+"&hub.challenge=123")).timeout(java.time.Duration.ofSeconds(2)).build(),HttpResponse.BodyHandlers.ofString());
                    if(r.statusCode()==200 && r.body().equals("123")){System.out.println("{\"ready\":true}");return;}
                }catch(Exception ignored){}
                Thread.sleep(500);
            }
            throw new IllegalStateException("Spring no disponible");
        }
        if(args[0].equals("paused")){
            byte[] body="{}".getBytes(StandardCharsets.UTF_8);
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            var r=CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+backendPort+"/webhooks/whatsapp"))
                .header("Content-Type","application/json").header("X-Hub-Signature-256","sha256="+HexFormat.of().formatHex(mac.doFinal(body)))
                .timeout(java.time.Duration.ofSeconds(10)).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofString());
            if(r.statusCode()!=503||!r.body().contains("RECHAZADO"))throw new IllegalStateException("Recepcion no pausada");
            System.out.println("{\"recepcion\":false,\"signed_post_status\":503}");return;
        }
        if(args[0].equals("durable-smoke")){
            byte[] body=("{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":\"qa-waba-local\",\"changes\":[{\"field\":\"messages\",\"value\":{\"messaging_product\":\"whatsapp\",\"metadata\":{\"phone_number_id\":\"qa-numero-local\"},\"messages\":[{\"id\":\"qa-durable-smoke\",\"from\":\"100000000000001\",\"timestamp\":\""+java.time.Instant.now().getEpochSecond()+"\",\"type\":\"text\",\"text\":{\"body\":\"SINTETICO_C3\"}}]}}]}]}").getBytes(StandardCharsets.UTF_8);
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            String signed="sha256="+HexFormat.of().formatHex(mac.doFinal(body));
            var endpoint=URI.create("http://127.0.0.1:"+backendPort+"/webhooks/whatsapp");
            var bad=CLIENT.send(HttpRequest.newBuilder(endpoint).timeout(java.time.Duration.ofSeconds(15)).header("Content-Type","application/json").header("X-Hub-Signature-256","sha256="+"0".repeat(64)).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofString());
            if(bad.statusCode()!=403)throw new IllegalStateException("HMAC no rechazada");
            byte[] unauthorized=new String(body,StandardCharsets.UTF_8).replace("qa-waba-local","qa-waba-no-autorizada").getBytes(StandardCharsets.UTF_8);
            String other="sha256="+HexFormat.of().formatHex(mac.doFinal(unauthorized));
            var rejected=CLIENT.send(HttpRequest.newBuilder(endpoint).timeout(java.time.Duration.ofSeconds(15)).header("Content-Type","application/json").header("X-Hub-Signature-256",other).POST(HttpRequest.BodyPublishers.ofByteArray(unauthorized)).build(),HttpResponse.BodyHandlers.ofString());
            if(rejected.statusCode()!=403)throw new IllegalStateException("WABA no rechazada");
            var request=HttpRequest.newBuilder(endpoint).timeout(java.time.Duration.ofSeconds(20)).header("Content-Type","application/json").header("X-Hub-Signature-256",signed).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var first=CLIENT.send(request,HttpResponse.BodyHandlers.ofString());
            var repeat=CLIENT.send(request,HttpResponse.BodyHandlers.ofString());
            if(first.statusCode()!=200||!first.body().matches("(?s).*\\\"aceptados\\\"\\s*:\\s*1(?:[,}]).*"))throw new IllegalStateException("Recepcion no aceptada");
            if(repeat.statusCode()!=200||!repeat.body().matches("(?s).*\\\"duplicados\\\"\\s*:\\s*1(?:[,}]).*"))throw new IllegalStateException("Deduplicacion incorrecta");
            System.out.println("{\"invalid_hmac_http\":403,\"unauthorized_waba_http\":403,\"first_http\":200,\"repeat_http\":200,\"accepted\":1,\"duplicates\":1}");return;
        }
        List<Long> times=new ArrayList<>();String prefix=UUID.randomUUID().toString();
        int count=Integer.parseInt(args[1]);
        for(int i=0;i<count;i++){
            long start=System.nanoTime();times.add(post(prefix+"-"+i,i%4+1));
            if(args[0].equals("load"))Thread.sleep(Math.max(0,500-(System.nanoTime()-start)/1_000_000));
        }
        var executor=Executors.newFixedThreadPool(4);List<Future<Long>> futures=new ArrayList<>();
        for(int i=0;i<4;i++)futures.add(executor.submit(()->post(prefix+"-duplicate",1)));
        for(var f:futures)times.add(f.get());executor.shutdown();
        Collections.sort(times);
        System.out.println("{\"requests\":"+times.size()+",\"unique_events\":"+(count+1)+",\"errors\":0,\"p95_ms\":"+times.get((int)Math.ceil(times.size()*.95)-1)+",\"p99_ms\":"+times.get((int)Math.ceil(times.size()*.99)-1)+",\"max_ms\":"+times.getLast()+"}");
    }
}
