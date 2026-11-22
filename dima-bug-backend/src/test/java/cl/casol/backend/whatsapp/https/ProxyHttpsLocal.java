package cl.casol.backend.whatsapp.https;

import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import java.net.*;
import java.net.http.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Proxy de QA, solo código de pruebas; no forma parte del artefacto productivo. */
final class ProxyHttpsLocal implements AutoCloseable {
    static final int MAX=3*1024*1024;
    private final HttpsServer servidor;
    private final ExecutorService executor=new ThreadPoolExecutor(8,8,0,TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(16),r->{var t=new Thread(r,"qa-proxy-https");t.setDaemon(true);return t;});
    private final HttpClient upstream=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private final int backend;
    private final ConcurrentLinkedQueue<String> registros=new ConcurrentLinkedQueue<>();
    final SSLContext clienteTls;
    ProxyHttpsLocal(Path carpeta,int backend)throws Exception {
        this.backend=backend;
        byte[] bytes=new byte[24];new SecureRandom().nextBytes(bytes);String password=HexFormat.of().formatHex(bytes);
        Path key=carpeta.resolve("localhost.p12");
        String exe=System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool";
        var p=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",exe).toString(),"-genkeypair","-alias","localhost",
            "-keyalg","RSA","-keysize","2048","-validity","2","-dname","CN=localhost","-ext","SAN=IP:127.0.0.1,DNS:localhost",
            "-storetype","PKCS12","-keystore",key.toString(),"-storepass:env","WA_QA_TLS_PASSWORD","-noprompt");
        var env=new HashMap<>(p.environment());p.environment().clear();
        for(String k:List.of("SystemRoot","WINDIR","SystemDrive","USERPROFILE","TEMP","TMP"))if(env.get(k)!=null)p.environment().put(k,env.get(k));
        p.environment().put("WA_QA_TLS_PASSWORD",password);p.redirectOutput(ProcessBuilder.Redirect.DISCARD);p.redirectError(ProcessBuilder.Redirect.DISCARD);
        var proceso=p.start();if(!proceso.waitFor(30,TimeUnit.SECONDS)){proceso.destroyForcibly();throw new IOException("Certificado QA no disponible");}
        if(proceso.exitValue()!=0)throw new IOException("Certificado QA no disponible");
        var almacen=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(key)){almacen.load(in,password.toCharArray());}
        var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(almacen,password.toCharArray());
        var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(almacen);
        SSLContext tls=SSLContext.getInstance("TLS");tls.init(km.getKeyManagers(),null,null);
        clienteTls=SSLContext.getInstance("TLS");clienteTls.init(null,tm.getTrustManagers(),null);
        servidor=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),16);
        servidor.setHttpsConfigurator(new HttpsConfigurator(tls){public void configure(HttpsParameters p){
            var ssl=getSSLContext().getDefaultSSLParameters();ssl.setProtocols(new String[]{"TLSv1.3","TLSv1.2"});p.setSSLParameters(ssl);
        }});
        servidor.setExecutor(executor);servidor.createContext("/",this::atender);servidor.start();
    }
    int puerto(){return servidor.getAddress().getPort();}
    String registros(){return String.join("\n",registros);}
    private void atender(HttpExchange x)throws IOException {
        int status=502;
        try{
            String metodo=x.getRequestMethod();URI uri=x.getRequestURI();
            if(uri.isAbsolute()||!"/webhooks/whatsapp".equals(uri.getRawPath())||!Set.of("GET","POST").contains(metodo)){
                status=403;responder(x,status,"RECHAZADO_PROXY".getBytes());return;
            }
            var lengths=x.getRequestHeaders().get("Content-Length");
            if(lengths!=null&&(lengths.size()!=1||!lengths.getFirst().matches("[0-9]{1,10}"))){status=400;responder(x,status,new byte[0]);return;}
            if(lengths!=null&&Long.parseLong(lengths.getFirst())>MAX){status=413;responder(x,status,new byte[0]);return;}
            byte[] cuerpo=x.getRequestBody().readNBytes(MAX+1);
            if(cuerpo.length>MAX){status=413;responder(x,status,new byte[0]);return;}
            if(metodo.equals("GET")&&cuerpo.length!=0){status=400;responder(x,status,new byte[0]);return;}
            if(x.getRequestHeaders().containsKey("Content-Encoding")){status=415;responder(x,status,new byte[0]);return;}
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+backend+uri.toASCIIString())).timeout(Duration.ofSeconds(25))
                .method(metodo,HttpRequest.BodyPublishers.ofByteArray(cuerpo));
            // Lista cerrada: no transmitir Authorization, cookies, Forwarded ni origen del cliente.
            for(String h:List.of("Content-Type","X-Hub-Signature-256")){
                var valores=x.getRequestHeaders().get(h);if(valores!=null){
                    if(valores.size()!=1){status=400;responder(x,status,new byte[0]);return;}b.header(h,valores.getFirst());
                }
            }
            var r=upstream.send(b.build(),HttpResponse.BodyHandlers.ofByteArray());status=r.statusCode();
            r.headers().firstValue("Content-Type").ifPresent(v->x.getResponseHeaders().set("Content-Type",v));
            x.getResponseHeaders().set("Cache-Control","no-store");responder(x,status,r.body());
        }catch(Exception e){status=502;try{responder(x,status,new byte[0]);}catch(IOException ignored){ }}
        finally{registros.add("wa_proxy status="+status);x.close();}
    }
    private void responder(HttpExchange x,int status,byte[] cuerpo)throws IOException {
        if(x.getRequestMethod().equals("HEAD")){x.sendResponseHeaders(status,-1);return;}
        x.sendResponseHeaders(status,cuerpo.length==0?-1:cuerpo.length);if(cuerpo.length>0)x.getResponseBody().write(cuerpo);
    }
    public void close(){servidor.stop(0);executor.shutdownNow();upstream.close();}
}
