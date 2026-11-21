package cl.casol.backend.shared.infrastructure.security;

import cl.casol.backend.identidad.infrastructure.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;

/**
 * Configuración de seguridad: define qué rutas son públicas, qué rol necesita cada una
 * y en qué orden se revisa el token en cada petición.
 */
@Configuration
public class SecurityConfig {

    /** BCrypt: transforma el código en un hash que no se puede revertir. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Reglas de acceso a la API. */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {

        http
                // CSRF apagado: el token va en el encabezado, no en una cookie
                .csrf(csrf -> csrf.disable())
                // Sin sesiones en el servidor: cada petición trae su token JWT
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 401 si no inició sesión, 403 si no tiene permiso
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                        )
                        .accessDeniedHandler((request, response, exception) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                        )
                )

                // Permisos por ruta: se revisan de arriba hacia abajo
                .authorizeHttpRequests(auth -> auth
                        // Callback de vinculación Gmail: valida estado/PKCE, no crea una sesión de DimaBug.
                        .requestMatchers(HttpMethod.GET,"/login/oauth2/code/google").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/publico/soluciones","/api/publico/soluciones/*","/api/publico/soluciones/*/materiales","/api/publico/soluciones/*/materiales/*/archivo").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/publico/chatbot/consultas").permitAll()
                        .requestMatchers("/api/publico/**").denyAll()
                        // Todo lo de administración es solo para el administrador
                        .requestMatchers("/api/admin/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST,"/api/procedimientos/*/ejecuciones").hasAnyRole("ADMINISTRADOR","TECNICO","SOPORTE")
                        .requestMatchers(HttpMethod.GET,"/api/procedimientos/*/historial").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST,"/api/procedimientos","/api/procedimientos/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PUT,"/api/procedimientos/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PATCH,"/api/procedimientos/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.DELETE,"/api/procedimientos/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/sucursales", "/api/sucursales/**")
                        .hasAnyRole("ADMINISTRADOR", "TECNICO", "SOPORTE")
                        .requestMatchers(HttpMethod.POST, "/api/chatbot/consultas")
                        .hasAnyRole("ADMINISTRADOR", "TECNICO", "SOPORTE", "USUARIO")
                        // Módulo de tickets: los permisos finos por rol se validan en cada caso de uso.
                        .requestMatchers("/api/tickets", "/api/tickets/**")
                        .hasAnyRole("ADMINISTRADOR", "TECNICO", "SOPORTE", "USUARIO")
                        .requestMatchers(HttpMethod.GET, "/api/etiquetas")
                        .hasAnyRole("ADMINISTRADOR", "TECNICO", "SOPORTE", "USUARIO")
                        // Las etiquetas las crea soporte al momento de etiquetar un ticket.
                        .requestMatchers("/api/etiquetas", "/api/etiquetas/**").hasAnyRole("SOPORTE", "TECNICO")
                        .requestMatchers(HttpMethod.DELETE, "/api/conocimientos/*")
                        .hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET,
                                "/api/conocimientos",
                                "/api/conocimientos/buscar",
                                "/api/conocimientos/**",
                                "/api/hardware",
                                "/api/hardware/**",
                                "/api/sistemas",
                                "/api/sistemas/**",
                                "/api/frecuencias",
                                "/api/frecuencias/**",
                                "/api/procedimientos",
                                "/api/procedimientos/**")
                        .hasAnyRole("ADMINISTRADOR", "TECNICO", "SOPORTE", "USUARIO")
                        .requestMatchers("/api/sistemas/**", "/api/hardware/**", "/api/conocimientos/**",
                                "/api/frecuencias/**", "/api/departamentos/**", "/api/pruebas/**",
                                "/api/procedimientos/**", "/api/ejecuciones/**")
                        .hasAnyRole("ADMINISTRADOR", "TECNICO", "SOPORTE")
                        .requestMatchers("/api/usuarios/**", "/api/roles/**")
                        .hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET, "/").permitAll()
                        .requestMatchers(
                                "/error",
                                "/api/health",
                                "/api/auth/login",
                                "/api/auth/codigo",
                                "/api/auth/codigo/verificar",
                                "/api/auth/recuperar/email",
                                "/api/auth/recuperar/codigo",
                                "/api/auth/recuperar/password"
                        ).permitAll()

                        // Cualquier otra ruta exige haber iniciado sesión
                        .anyRequest().authenticated()
                )

                // Antes de revisar permisos, el filtro JWT identifica al usuario
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        org.springframework.security.web.authentication
                                .UsernamePasswordAuthenticationFilter.class
                )
                .addFilterAfter(new cl.casol.backend.identidad.infrastructure.security.AlcanceBasicoFilter(),
                        cl.casol.backend.identidad.infrastructure.security.JwtAuthenticationFilter.class);

        return http.build();
    }
}
