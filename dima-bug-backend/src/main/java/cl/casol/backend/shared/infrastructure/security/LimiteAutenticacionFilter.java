package cl.casol.backend.shared.infrastructure.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;

/** Protección local acotada. En varias instancias se requiere también límite compartido en el borde. */
@Component @Order(-89)
public class LimiteAutenticacionFilter extends OncePerRequestFilter {
    // Devuelve ventana.
    private record Ventana(long inicio, int cantidad) {}
    private final Map<String, Ventana> ventanas = new HashMap<>();
    private long inicio = System.nanoTime();
    private int total;
    private final int maximoIp;
    public LimiteAutenticacionFilter(@org.springframework.beans.factory.annotation.Value("${dimabug.auth.limite-ip-minuto:20}") int maximoIp) {
        this.maximoIp = Math.max(1, maximoIp);
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest r) {
        String ruta = r.getServletPath();
        if (ruta == null || ruta.isEmpty()) ruta = r.getRequestURI();
        return !"POST".equals(r.getMethod()) || !ruta.startsWith("/api/auth/");
    }
    // Indica si se puede admitir.
    private synchronized boolean admitir(String ip) {
        long ahora = System.nanoTime(), minuto = 60_000_000_000L;
        if (ahora - inicio >= minuto) {
            inicio = ahora; total = 0;
            ventanas.entrySet().removeIf(e -> ahora - e.getValue().inicio() >= minuto);
        }
        if (total >= 300) return false;
        var v = ventanas.get(ip);
        if (v == null || ahora - v.inicio() >= minuto) {
            if (v == null && ventanas.size() >= 4096) return false;
            v = new Ventana(ahora, 0);
        }
        if (v.cantidad() >= maximoIp) return false;
        ventanas.put(ip, new Ventana(v.inicio(), v.cantidad() + 1)); total++;
        return true;
    }
    @Override protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain chain)
            throws ServletException, IOException {
        s.setHeader("Cache-Control", "no-store");
        if (!admitir(r.getRemoteAddr())) {
            s.setStatus(429); s.setHeader("Retry-After", "60"); s.setContentType("application/json");
            s.getWriter().write("{\"message\":\"Demasiadas solicitudes. Espere un minuto.\"}"); return;
        }
        byte[] body = r.getInputStream().readNBytes(4097);
        if (body.length > 4096) { s.setStatus(413); return; }
        var input = new java.io.ByteArrayInputStream(body);
        var limitado = new HttpServletRequestWrapper(r) {
            @Override public int getContentLength() { return body.length; }
            @Override public long getContentLengthLong() { return body.length; }
            @Override public ServletInputStream getInputStream() {
                return new ServletInputStream() {
                    public int read() { return input.read(); }
                    public int read(byte[] b, int off, int len) { return input.read(b, off, len); }
                    public boolean isFinished() { return input.available() == 0; }
                    public boolean isReady() { return true; }
                    public void setReadListener(ReadListener listener) { throw new IllegalStateException("Lectura síncrona"); }
                };
            }
            @Override public java.io.BufferedReader getReader() {
                return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            }
        };
        chain.doFilter(limitado, s);
    }
}
