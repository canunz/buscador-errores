package cl.casol.backend.whatsapp.infrastructure.http;

import cl.casol.backend.conocimiento.application.service.ConsultarChatbotService;
import cl.casol.backend.whatsapp.application.service.ProcesarMensajeService;
import cl.casol.backend.whatsapp.application.port.out.ReconocerImagenPort;
import cl.casol.backend.whatsapp.application.port.out.AlmacenWhatsAppPort;
import org.springframework.beans.factory.ObjectProvider;
import cl.casol.backend.whatsapp.infrastructure.busqueda.BusquedaPublicaCanalAdapter;
import cl.casol.backend.whatsapp.infrastructure.ocr.OcrNodeProcesoAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;

@Configuration(proxyBeanMethods=false)
@Profile(WebhookHttpLocalConfig.PERFIL)
public class WebhookHttpLocalConfig {
    public static final String PERFIL="qa-whatsapp-http-local & !staging & !prod & !production";
    @Bean ValidacionWebhookLocal validacionWebhookLocal(
            @Value("${dimabug.whatsapp.http-local.verify-token}")String token,
            @Value("${dimabug.whatsapp.http-local.app-secret}")String secreto) {
        return new ValidacionWebhookLocal(token,secreto);
    }
    @Bean ReconocerImagenPort ocrWebhookHttpLocal(@Value("${dimabug.whatsapp.qa.raiz}")String raiz) {
        return new OcrNodeProcesoAdapter(Path.of(raiz));
    }
    @Bean ProcesarMensajeService procesadorWebhookHttpLocal(ConsultarChatbotService buscador,
            @Qualifier("ocrWebhookHttpLocal")ReconocerImagenPort ocr) {
        return new ProcesarMensajeService(new BusquedaPublicaCanalAdapter(buscador),ocr,Clock.systemUTC());
    }
    @Bean EntradaWebhookHttpAdapter entradaWebhookHttpLocal(ProcesarMensajeService servicio,ValidacionWebhookLocal validacion,
            ObjectProvider<AlmacenWhatsAppPort> almacenes) {
        var almacen=almacenes.getIfAvailable();
        return almacen==null?new EntradaWebhookHttpAdapter(servicio,validacion):new EntradaWebhookHttpAdapter(almacen,validacion);
    }
    @Bean @Order(1) SecurityFilterChain seguridadWebhookHttpLocal(HttpSecurity http)throws Exception {
        return seguridadLoopback(http);
    }
    /** Compartida por perfiles locales; no abre otras rutas ni confía en Forwarded. */
    public static SecurityFilterChain seguridadLoopback(HttpSecurity http)throws Exception {
        http.securityMatcher(request -> "/webhooks/whatsapp".equals(ruta(request)))
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c->c.disable())
            .csrf(c->c.ignoringRequestMatchers(request->"POST".equals(request.getMethod()) && "/webhooks/whatsapp".equals(ruta(request))))
            .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.GET,"/webhooks/whatsapp").permitAll()
                    .requestMatchers(HttpMethod.POST,"/webhooks/whatsapp").permitAll().anyRequest().denyAll())
            .addFilterBefore(new SoloLoopback(),CsrfFilter.class);
        return http.build();
    }
    private static String ruta(HttpServletRequest r) {
        String path=r.getServletPath();
        return path==null || path.isEmpty()?r.getRequestURI().substring(r.getContextPath().length()):path;
    }
    /** No bean servlet: se ejecuta únicamente dentro de la cadena de la ruta exacta. */
    private static final class SoloLoopback extends OncePerRequestFilter {
        protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws IOException,ServletException {
            if(!Set.of("127.0.0.1","::1","0:0:0:0:0:0:0:1").contains(r.getRemoteAddr())) {
                s.setStatus(403);s.setHeader("Cache-Control","no-store");return;
            }
            chain.doFilter(r,s);
        }
    }
}
