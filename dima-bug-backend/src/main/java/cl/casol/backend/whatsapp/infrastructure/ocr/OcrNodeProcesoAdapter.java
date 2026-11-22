package cl.casol.backend.whatsapp.infrastructure.ocr;

import cl.casol.backend.whatsapp.application.port.out.ReconocerImagenPort;
import cl.casol.backend.whatsapp.domain.RespuestaCanal.Candidato;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Sin shell, red, rutas del evento, HTTP ni credenciales heredadas. */
public final class OcrNodeProcesoAdapter implements ReconocerImagenPort {
    private static final Set<String> MEDIOS=Set.of("qa-media-legible","qa-media-ambiguo","qa-media-borroso","qa-media-vacia","qa-media-corrupta");
    private final Path raiz, node, puente, tmp;
    private final Duration plazo;
    public OcrNodeProcesoAdapter(Path raiz) { this(raiz,Duration.ofSeconds(35)); }
    public OcrNodeProcesoAdapter(Path raiz,Duration plazo) { this(raiz,null,null,plazo); }
    /**
     * node y tmp opcionales (null = valores QA de Windows bajo la raíz). En Linux/systemd: node = binario Node 22
     * absoluto y tmp dentro del StateDirectory, porque ProtectSystem=strict deja la raíz /opt en solo lectura.
     */
    public OcrNodeProcesoAdapter(Path raiz,Path node,Path tmp,Duration plazo) {
        this.raiz=raiz.toAbsolutePath().normalize();this.plazo=plazo;
        if (plazo.isNegative() || plazo.isZero() || plazo.compareTo(Duration.ofSeconds(35))>0) throw new IllegalArgumentException("Plazo QA inválido");
        if (node!=null && !node.isAbsolute() || tmp!=null && !tmp.isAbsolute()) throw new IllegalArgumentException("Rutas OCR deben ser absolutas");
        this.node=node!=null?node.normalize():this.raiz.resolve("qa/runtime/tools/node-v22.23.5-win-x64/node.exe");
        this.tmp=tmp!=null?tmp.normalize():this.raiz.resolve("qa/runtime/tmp");
        puente=this.raiz.resolve("qa/whatsapp/ocr-bridge.cjs");
    }
    public List<Candidato> reconocer(String medio) {
        if (!MEDIOS.contains(medio)) throw new FalloOcr();
        return ejecutar(medio,null);
    }
    /** Imagen real (PNG, JPEG o WEBP) entregada por stdin: el puente nunca recibe rutas ni red. */
    public List<Candidato> reconocerBytes(byte[] imagen) {
        if (imagen==null || imagen.length==0 || imagen.length>MAX_IMAGEN) throw new FalloOcr();
        return ejecutar("--stdin",imagen);
    }
    public static final int MAX_IMAGEN=5*1024*1024;
    private List<Candidato> ejecutar(String argumento,byte[] entrada) {
        if (!Files.isRegularFile(node) || !Files.isRegularFile(puente)) throw new FalloOcr();
        Process proceso=null;
        var executor=Executors.newVirtualThreadPerTaskExecutor();
        try {
            var pb=new ProcessBuilder(node.toString(),puente.toString(),argumento).directory(raiz.toFile());
            pb.environment().clear();
            for (String key:List.of("SystemRoot","WINDIR","SystemDrive")) {
                String value=System.getenv(key);if(value!=null)pb.environment().put(key,value);
            }
            Files.createDirectories(tmp);
            pb.environment().put("TEMP",tmp.toString());pb.environment().put("TMP",tmp.toString());
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            proceso=pb.start();
            Process activo=proceso;
            Future<byte[]> salida=executor.submit(()->{
                byte[] data=activo.getInputStream().readNBytes(65537);
                if(data.length>65536) { terminar(activo);throw new IOException("Respuesta demasiado grande"); }
                return data;
            });
            // La escritura también queda acotada por el plazo: si Node no lee, se termina el proceso.
            executor.submit(()->{try(var stdin=activo.getOutputStream()){if(entrada!=null)stdin.write(entrada);}return null;});
            try {
                byte[] bytes=salida.get(plazo.toMillis(),TimeUnit.MILLISECONDS);
                if(!activo.waitFor(1000,TimeUnit.MILLISECONDS)) throw new TimeoutException();
                if(activo.exitValue()!=0)throw new FalloOcr();
                var tree=JsonMapper.builder().build().readTree(bytes).path("candidatos");
                if(!tree.isArray() || tree.size()>3)throw new FalloOcr();
                var resultado=new ArrayList<Candidato>();
                for(var c:tree) {
                    if(!c.path("texto").isString() || c.path("texto").asText().length()>20000 || !c.path("avisos").isArray() || c.path("avisos").size()>10)throw new FalloOcr();
                    var avisos=new ArrayList<String>();
                    for(var a:c.path("avisos")) { if(!a.isString() || a.asText().length()>1000)throw new FalloOcr();avisos.add(a.asText()); }
                    resultado.add(new Candidato(c.path("texto").asText(),avisos));
                }
                return List.copyOf(resultado);
            } catch(TimeoutException e) { terminar(activo);salida.cancel(true);throw new PlazoOcr(); }
              catch(ExecutionException e) { throw new FalloOcr(); }
        } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new FalloOcr(); }
          catch(IOException e) { throw new FalloOcr(); }
        finally { if(proceso!=null)terminar(proceso); executor.shutdownNow(); }
    }
    private static void terminar(Process proceso) {
        proceso.descendants().forEach(ProcessHandle::destroyForcibly);
        proceso.destroyForcibly();
    }
}
