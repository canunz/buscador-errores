package cl.casol.backend.correo.infrastructure.web;

import cl.casol.backend.correo.application.service.*;
import cl.casol.backend.correo.infrastructure.gmail.GmailConfig;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.net.URI;
import java.util.Map;

@RestController
public class GmailOAuthController {
    private static final String COOKIE="dimabug_gmail_oauth";
    private final GmailOAuthService servicio;private final GmailConfig config;
    public GmailOAuthController(GmailOAuthService servicio,GmailConfig config){this.servicio=servicio;this.config=config;}
    private ResponseCookie cookie(String valor,int segundos){return ResponseCookie.from(COOKIE,valor).path("/login/oauth2/code/google").httpOnly(true).secure(config.callback.startsWith("https:")).sameSite("Lax").maxAge(Duration.ofSeconds(segundos)).build();}
    @GetMapping("/api/admin/gmail") public GmailOAuthService.Estado estado(Authentication a,HttpServletResponse r){r.setHeader("Cache-Control","no-store");return servicio.estado(a.getName());}
    @PostMapping("/api/admin/gmail/autorizacion") public ResponseEntity<Map<String,String>> iniciar(Authentication a){
        var inicio=servicio.iniciar(a.getName());return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.SET_COOKIE,cookie(inicio.navegador(),300).toString()).body(Map.of("authorizationUrl",inicio.url()));
    }
    @PostMapping("/api/admin/gmail/revocar") public GmailOAuthService.Estado revocar(Authentication a,HttpServletResponse r){r.setHeader("Cache-Control","no-store");return servicio.revocar(a.getName());}
    @GetMapping("/login/oauth2/code/google") public ResponseEntity<?> callback(
        @RequestParam(required=false) String state,@RequestParam(required=false) String code,@RequestParam(required=false) String error,
        @CookieValue(name=COOKIE,required=false) String navegador){
        var headers=new HttpHeaders();headers.add(HttpHeaders.SET_COOKIE,cookie("",0).toString());headers.setCacheControl("no-store");headers.set("Referrer-Policy","no-referrer");headers.set("Content-Security-Policy","default-src 'none'");
        try{servicio.completar(state,navegador,code,error);headers.setLocation(URI.create(config.frontend));return new ResponseEntity<>(null,headers,HttpStatus.SEE_OTHER);}
        catch(GmailException ex){return new ResponseEntity<>(Map.of("message",ex.getMessage()),headers,HttpStatus.BAD_REQUEST);}
        catch(org.springframework.security.access.AccessDeniedException ex){return new ResponseEntity<>(Map.of("message","El administrador ya no está autorizado para vincular Gmail"),headers,HttpStatus.FORBIDDEN);}
    }
}
