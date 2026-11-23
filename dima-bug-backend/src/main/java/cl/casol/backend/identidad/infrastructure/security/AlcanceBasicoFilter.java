package cl.casol.backend.identidad.infrastructure.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
/** Fail-closed allowlist applied before legacy role rules. Not a servlet bean. */
public final class AlcanceBasicoFilter extends OncePerRequestFilter {
    public static boolean permitido(String method,String path) {
        return "GET".equals(method) && (path.equals("/api/auth/me") || path.equals("/api/health")
            || path.equals("/api/tickets") || path.equals("/api/tickets/conteo") || path.equals("/api/tickets/solicitudes/limites") || path.equals("/api/tickets/solicitudes/catalogos") || path.matches("/api/tickets/[1-9][0-9]*")
            || path.matches("/api/tickets/[1-9][0-9]*/adjuntos/[1-9][0-9]*"))
            || "POST".equals(method) && (path.equals("/api/tickets") || path.equals("/api/tickets/solicitudes"));
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
            throws IOException,ServletException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        String path=req.getServletPath();
        if(path==null || path.isEmpty()) path=req.getRequestURI().substring(req.getContextPath().length());
        if (auth!=null && auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("SCOPE_TICKETS_PROPIOS"))
                && !permitido(req.getMethod(),path)) {
            res.sendError(403); return;
        }
        chain.doFilter(req,res);
    }
}
