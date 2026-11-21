package cl.casol.backend.identidad.infrastructure.security;

import cl.casol.backend.identidad.application.port.out.TokenServicePort;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.Usuario;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Filtro que se ejecuta en cada petición.
 * Lee el token, revisa que sea válido y que el usuario siga activo,
 * y deja registrado quién es y qué rol tiene.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final TokenServicePort tokenService;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioAutenticadoCache usuariosAutenticados;

    public JwtAuthenticationFilter(
            TokenServicePort tokenService,
            UsuarioRepository usuarioRepository,
            UsuarioAutenticadoCache usuariosAutenticados
    ) {
        this.tokenService = tokenService;
        this.usuarioRepository = usuarioRepository;
        this.usuariosAutenticados = usuariosAutenticados;
    }

    // Se ejecuta en cada petición antes de llegar al controlador.
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Lee el encabezado Authorization
        String authorizationHeader =
                request.getHeader("Authorization");

        // Sin token: sigue como petición anónima (las rutas privadas la rechazan después)
        if (authorizationHeader == null) {
            log.debug("JWT: falta header Authorization para {} {}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorizationHeader.startsWith("Bearer ")) {
            log.debug("JWT: header Authorization no comienza con 'Bearer ' para {} {}",
                    request.getMethod(), request.getRequestURI());

            filterChain.doFilter(request, response);
            return;
        }

        // 2. Saca el token quitando la palabra "Bearer "
        String token =
                authorizationHeader.substring(7);

        log.debug("JWT: token recibido con longitud {} para {} {}",
                token.length(), request.getMethod(), request.getRequestURI());

        // 3. Revisa la firma y que no esté vencido
        boolean tokenValido = !token.isBlank() && tokenService.esValido(token);
        log.debug("JWT: resultado de validacion={}", tokenValido);

        if (!tokenValido) {
            filterChain.doFilter(request, response);
            return;
        }

        String email;
        try {
            // 4. Obtiene el correo que va dentro del token
            email = tokenService.obtenerEmail(token);
            log.debug("JWT: identidad extraída");
        } catch (RuntimeException exception) {
            log.debug("JWT: no fue posible extraer el email; tipo={}",
                    exception.getClass().getSimpleName());
            filterChain.doFilter(request, response);
            return;
        }

        // 5. Busca al usuario en la base de datos
        Usuario usuario = usuarioRepository.buscarPorEmail(email).orElse(null);

        log.debug("JWT: usuario encontrado={}", usuario != null);

        if (usuario != null) {
            log.debug("JWT: usuario activo={}", usuario.isActivo());
        }

        // 6. Solo si está activo se le da acceso con su rol (por ejemplo ROLE_SOPORTE)
        if (usuario != null && usuario.puedeIngresar()) {
            String rolNombre = usuario.getRol().getNombre();
            log.debug("JWT: nombre exacto del rol={}", rolNombre);

            String authorityName = "ROLE_" + rolNombre.trim().toUpperCase(Locale.ROOT);
            log.debug("JWT: autoridad construida={}", authorityName);

            if (SecurityContextHolder.getContext().getAuthentication() == null) {

            SimpleGrantedAuthority authority =
                    new SimpleGrantedAuthority(authorityName);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            usuario.getEmail(),
                            null,
                            usuario.isAccesoBasico() ? List.of(authority, new SimpleGrantedAuthority("SCOPE_TICKETS_PROPIOS")) : List.of(authority)
                    );
            // Guarda al usuario como autenticado para el resto de la petición
            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);

            log.debug("JWT: autenticacion instalada en SecurityContextHolder={}",
                    SecurityContextHolder.getContext().getAuthentication() != null);
            } else {
                log.debug("JWT: SecurityContextHolder ya contenia una autenticacion");
            }
        }

        filterChain.doFilter(request, response);
    }
}
