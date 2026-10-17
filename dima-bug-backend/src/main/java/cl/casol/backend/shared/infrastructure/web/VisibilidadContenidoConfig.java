package cl.casol.backend.shared.infrastructure.web;
import cl.casol.backend.shared.application.service.VisibilidadContenidoService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.http.*;
import java.util.regex.Pattern;
@Configuration
public class VisibilidadContenidoConfig implements WebMvcConfigurer {
    private final VisibilidadContenidoService service;
    public VisibilidadContenidoConfig(VisibilidadContenidoService service) { this.service=service; }
    public void addInterceptors(InterceptorRegistry registry) {
        Pattern patron=Pattern.compile("^/api/(conocimientos|procedimientos)/(\\d+)(?:/.*)?$");
        registry.addInterceptor(new HandlerInterceptor() {
            public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
                if (!"GET".equals(request.getMethod())) return true;
                var match=patron.matcher(request.getRequestURI().substring(request.getContextPath().length()));
                if (!match.matches()) return true;
                var auth=SecurityContextHolder.getContext().getAuthentication();
                boolean admin=auth!=null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));
                boolean staff=auth!=null && auth.getAuthorities().stream().anyMatch(a -> java.util.Set.of("ROLE_ADMINISTRADOR","ROLE_TECNICO","ROLE_SOPORTE").contains(a.getAuthority()));
                boolean historico=request.getRequestURI().endsWith("/resultados") || request.getRequestURI().endsWith("/efectividad");
                service.verificar(match.group(1),Integer.valueOf(match.group(2)),staff,admin,historico);
                return true;
            }
        }).addPathPatterns("/api/conocimientos/**","/api/procedimientos/**");
    }
}
