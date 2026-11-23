package cl.casol.backend.whatsapp.infrastructure.busqueda;

import cl.casol.backend.whatsapp.application.port.out.FalloCatalogoPublico;
import cl.casol.backend.whatsapp.domain.RespuestaCanal.Solucion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Una petición por intento durable. Sin redirecciones, proxy Graph, credenciales ni logs de cuerpos. */
public final class CatalogoPublicoHttpClient {
    static final int MAX_BYTES=262144;
    private final URI base;
    private final HttpClient http;
    private final Duration plazo;
    private final Clock reloj;
    private final JsonMapper json=JsonMapper.builder().build();

    public CatalogoPublicoHttpClient(String url,Duration conexion,Duration respuesta){
        this(url,conexion,respuesta,Clock.systemUTC());
    }
    CatalogoPublicoHttpClient(String url,Duration conexion,Duration respuesta,Clock reloj){
        try { base=URI.create(url); } catch(RuntimeException e){throw new IllegalArgumentException("Origen de catálogo inválido");}
        if(!"http".equals(base.getScheme()) || !"127.0.0.1".equals(base.getHost()) ||
                base.getPort()<1 || base.getPort()>65535 || base.getRawUserInfo()!=null ||
                base.getRawQuery()!=null || base.getRawFragment()!=null ||
                !(base.getRawPath().isEmpty() || base.getRawPath().equals("/")))
            throw new IllegalArgumentException("El catálogo requiere un origen HTTP IPv4 loopback explícito");
        validarPlazo(conexion);validarPlazo(respuesta);
        plazo=respuesta;this.reloj=Objects.requireNonNull(reloj);
        http=HttpClient.newBuilder().connectTimeout(conexion).followRedirects(HttpClient.Redirect.NEVER)
                .proxy(new ProxySelector(){
                    public List<Proxy> select(URI uri){return List.of(Proxy.NO_PROXY);}
                    public void connectFailed(URI uri,SocketAddress address,java.io.IOException e){ }
                }).build();
    }
    private static void validarPlazo(Duration d){
        if(d==null || d.toMillis()<1 || d.compareTo(Duration.ofSeconds(5))>0)
            throw new IllegalArgumentException("Plazo de catálogo fuera de 1..5000 ms");
    }
    public int puerto(){return base.getPort();}
    public String catalogo(){return "principal-publico-v1@http://127.0.0.1:"+base.getPort();}

    public List<Solucion> buscar(String consulta){
        if(consulta==null || consulta.isBlank() || consulta.length()>300)throw new IllegalArgumentException("Consulta inválida");
        var request=HttpRequest.newBuilder(base.resolve("/api/publico/chatbot/consultas")).timeout(plazo)
                .header("Accept","application/json").header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(Map.of("consulta",consulta.strip())))).build();
        var body=peticion(request,false);
        try {
            JsonNode soluciones=json.readTree(body).path("soluciones");
            if(!soluciones.isArray() || soluciones.size()>5)throw new IllegalArgumentException();
            var result=new ArrayList<Solucion>();var ids=new HashSet<Integer>();
            for(var s:soluciones){
                var mapped=solucion(s,"solucion");
                if(!ids.add(mapped.id()))throw new IllegalArgumentException();
                result.add(mapped);
            }
            return List.copyOf(result);
        } catch(RuntimeException e){throw fallo();}
    }
    public Optional<Solucion> publica(int id){
        if(id<1)throw new IllegalArgumentException("Identificador de solución inválido");
        var request=HttpRequest.newBuilder(base.resolve("/api/publico/soluciones/"+id)).timeout(plazo)
                .header("Accept","application/json").GET().build();
        byte[] body=peticion(request,true);
        if(body==null)return Optional.empty();
        try {
            var result=solucion(json.readTree(body),"instrucciones");
            if(result.id()!=id)throw new IllegalArgumentException();
            return Optional.of(result);
        } catch(RuntimeException e){throw fallo();}
    }
    private Solucion solucion(JsonNode node,String instrucciones){
        JsonNode id=node.path("id"),titulo=node.path("titulo"),texto=node.path(instrucciones);
        if(!id.isIntegralNumber() || !id.canConvertToInt() || id.asInt()<1 || !texto.isString() ||
                texto.asString().isBlank() || !(titulo.isNull() || titulo.isString()) ||
                titulo.isString() && titulo.asString().length()>1000)throw new IllegalArgumentException();
        return new Solucion(id.asInt(),titulo.isNull()?"Solución de autoservicio":titulo.asString(),texto.asString(),catalogo());
    }
    private byte[] peticion(HttpRequest request,boolean admite404){
        CompletableFuture<HttpResponse<byte[]>> future=http.sendAsync(request,info -> new Limitado());
        try {
            var r=future.get(plazo.toMillis(),TimeUnit.MILLISECONDS);
            if(r.statusCode()==404 && admite404)return null;
            if(r.statusCode()!=200){
                boolean retry=r.statusCode()==429 || r.statusCode()==408 || r.statusCode()>=500;
                throw new FalloCatalogoPublico(retry,retryAfter(r.headers().firstValue("Retry-After").orElse("")));
            }
            if(!r.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT).split(";",2)[0].trim().equals("application/json"))throw fallo();
            return r.body();
        } catch(InterruptedException e){Thread.currentThread().interrupt();future.cancel(true);throw fallo();}
          catch(ExecutionException|TimeoutException e){future.cancel(true);throw fallo();}
    }
    private Duration retryAfter(String value){
        try {
            long seconds=value.matches("[0-9]{1,12}")?Long.parseLong(value):Duration.between(reloj.instant(),
                    ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds();
            return Duration.ofSeconds(Math.max(0,Math.min(86400,seconds)));
        }catch(RuntimeException e){return Duration.ZERO;}
    }
    private static FalloCatalogoPublico fallo(){return new FalloCatalogoPublico(true,Duration.ZERO);}

    /** Cota aplicada durante la recepción, no después de reservar un cuerpo arbitrariamente grande. */
    private static final class Limitado implements HttpResponse.BodySubscriber<byte[]> {
        final CompletableFuture<byte[]> result=new CompletableFuture<>();
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> data){
            for(var buffer:data){
                if(buffer.remaining()>MAX_BYTES-bytes.size()){
                    subscription.cancel();result.completeExceptionally(fallo());return;
                }
                byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable ignored){result.completeExceptionally(fallo());}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
}
