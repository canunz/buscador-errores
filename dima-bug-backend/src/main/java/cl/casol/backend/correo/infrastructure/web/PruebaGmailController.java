package cl.casol.backend.correo.infrastructure.web;
import cl.casol.backend.correo.application.service.PruebaGmailService;
import cl.casol.backend.correo.application.port.out.PruebaGmailStore;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.HttpServletResponse;
@RestController @RequestMapping("/api/admin/gmail/prueba")
public class PruebaGmailController {
    private final PruebaGmailService servicio;public PruebaGmailController(PruebaGmailService servicio){this.servicio=servicio;}
    public record Solicitud(@NotNull java.util.UUID clave,java.util.UUID anterior,@AssertTrue boolean confirmado,@NotBlank @Size(max=120) String destinatario){}
    @GetMapping public PruebaGmailService.EstadoPrueba estado(Authentication a,HttpServletResponse r){r.setHeader("Cache-Control","no-store");return servicio.estado(a.getName());}
    @PostMapping public PruebaGmailStore.Prueba enviar(Authentication a,@Valid @RequestBody Solicitud s,HttpServletResponse r){r.setHeader("Cache-Control","no-store");return servicio.enviar(a.getName(),s.clave().toString(),s.anterior()==null?null:s.anterior().toString(),s.confirmado(),s.destinatario());}
}
