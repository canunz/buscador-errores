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

//PASO IV: Enpoint público
//Filtro JWT Paso 4: Modificamos. Conecta el filtro con Spring Security. Fin.
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                //Login, JWT, Cliente conserva JWT, Cada request trae JWT, Servidor valida JWT
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                        )
                        .accessDeniedHandler((request, response, exception) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/**").hasRole("ADMINISTRADOR")
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
                                "/api/auth/login"
                        ).permitAll()

                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        org.springframework.security.web.authentication
                                .UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
