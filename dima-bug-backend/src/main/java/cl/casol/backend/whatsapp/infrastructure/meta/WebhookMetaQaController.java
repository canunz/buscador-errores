package cl.casol.backend.whatsapp.infrastructure.meta;

import cl.casol.backend.whatsapp.infrastructure.http.ValidacionWebhookLocal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;
import static cl.casol.backend.whatsapp.infrastructure.http.ValidacionWebhookLocal.*;

@RestController
@Profile("qa-whatsapp-meta & !staging & !prod & !production")
@RequestMapping("/webhooks/whatsapp")
public final class WebhookMetaQaController {
    private final ValidacionWebhookLocal validacion;
    private final EntradaMetaQaAdapter entrada;
    private final ObservabilidadMetaQa observabilidad;
    public WebhookMetaQaController(ValidacionWebhookLocal v,EntradaMetaQaAdapter e,ObservabilidadMetaQa o){validacion=v;entrada=e;observabilidad=o;}
    @GetMapping(produces=MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verificar(HttpServletRequest r){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(new MediaType("text","plain",java.nio.charset.StandardCharsets.UTF_8))
            .body(validacion.verificar(parametro(r,"hub.mode"),parametro(r,"hub.verify_token"),parametro(r,"hub.challenge")));
    }
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EntradaMetaQaAdapter.Acuse> recibir(HttpServletRequest r)throws IOException{
        if(r.getContentLengthLong()>EntradaMetaQaAdapter.MAX_BYTES){observabilidad.registrar(ObservabilidadMetaQa.Codigo.TAMANO);throw new Rechazo(Motivo.TAMANO);}
        var firmas=Collections.list(r.getHeaders("X-Hub-Signature-256"));
        if(firmas.size()!=1){observabilidad.registrar(ObservabilidadMetaQa.Codigo.FIRMA);throw new Rechazo(Motivo.FIRMA);}
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(entrada.recibir(r.getInputStream().readNBytes(EntradaMetaQaAdapter.MAX_BYTES+1),firmas.getFirst()));
    }
    private String parametro(HttpServletRequest r,String nombre){var valores=r.getParameterValues(nombre);if(valores==null||valores.length!=1)throw new Rechazo(Motivo.PARAMETROS);return valores[0];}
    @ExceptionHandler(Rechazo.class) public ResponseEntity<Map<String,String>> rechazo(Rechazo e){return error(switch(e.motivo()){
        case FIRMA,PARAMETROS->403;case TAMANO->413;case EVENTO->400;case INTERNO->503;});}
    @ExceptionHandler(PoliticaMetaQa.NoAutorizado.class) public ResponseEntity<Map<String,String>> noAutorizado(PoliticaMetaQa.NoAutorizado e,HttpServletRequest r){
        r.setAttribute(WebhookMetaQaObservabilidad.VALIDACION,e.validacion().name());
        r.setAttribute(WebhookMetaQaObservabilidad.ELEMENTO,e.elemento());
        return error(403);
    }
    @ExceptionHandler(EntradaMetaQaAdapter.Pausado.class) public ResponseEntity<Map<String,String>> pausado(){return error(503);}
    @ExceptionHandler(IOException.class) public ResponseEntity<Map<String,String>> lectura(){observabilidad.registrar(ObservabilidadMetaQa.Codigo.EVENTO);return error(400);}
    private ResponseEntity<Map<String,String>> error(int status){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("estado","RECHAZADO"));}
}
