package cl.casol.backend.whatsapp.infrastructure.http;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;
import static cl.casol.backend.whatsapp.infrastructure.http.ValidacionWebhookLocal.*;

@RestController
@Profile(WebhookHttpLocalConfig.PERFIL)
@RequestMapping("/webhooks/whatsapp")
public final class WebhookWhatsAppController {
    private final ValidacionWebhookLocal validacion;
    private final EntradaWebhookHttpAdapter entrada;
    public WebhookWhatsAppController(ValidacionWebhookLocal validacion,EntradaWebhookHttpAdapter entrada) {
        this.validacion=validacion;this.entrada=entrada;
    }
    @GetMapping(produces=MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verificar(HttpServletRequest request) {
        return ResponseEntity.ok().contentType(new MediaType("text","plain",java.nio.charset.StandardCharsets.UTF_8)).cacheControl(CacheControl.noStore()).body(validacion.verificar(
                parametro(request,"hub.mode"),parametro(request,"hub.verify_token"),parametro(request,"hub.challenge")));
    }
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EntradaWebhookHttpAdapter.Recepcion> recibir(HttpServletRequest request)throws IOException {
        if(request.getContentLengthLong()>MAX_BYTES)throw new Rechazo(Motivo.TAMANO);
        var firmas=Collections.list(request.getHeaders("X-Hub-Signature-256"));
        if(firmas.size()!=1)throw new Rechazo(Motivo.FIRMA);
        byte[] cuerpo=request.getInputStream().readNBytes(MAX_BYTES+1); // También limita chunked/Content-Length ausente.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(entrada.recibir(cuerpo,firmas.getFirst()));
    }
    private String parametro(HttpServletRequest r,String nombre) {
        var valores=r.getParameterValues(nombre);
        if(valores==null || valores.length!=1)throw new Rechazo(Motivo.PARAMETROS);
        return valores[0];
    }
    @ExceptionHandler(Rechazo.class)
    public ResponseEntity<Map<String,String>> rechazo(Rechazo e) {
        HttpStatus estado=switch(e.motivo()) {
            case FIRMA,PARAMETROS -> HttpStatus.FORBIDDEN;
            case TAMANO -> HttpStatus.PAYLOAD_TOO_LARGE;
            case EVENTO -> HttpStatus.BAD_REQUEST;
            case INTERNO -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(estado).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(Map.of("estado","RECHAZADO"));
    }
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String,String>> lecturaFallida() {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(Map.of("estado","RECHAZADO"));
    }
}
