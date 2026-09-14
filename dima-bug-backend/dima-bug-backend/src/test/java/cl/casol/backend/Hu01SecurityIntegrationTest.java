package cl.casol.backend;

import cl.casol.backend.identidad.application.port.out.TokenServicePort;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.application.service.AutenticarUsuarioService;
import cl.casol.backend.identidad.domain.Rol;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.domain.exception.CredencialesInvalidasException;
import cl.casol.backend.identidad.domain.exception.UsuarioInactivoException;
import cl.casol.backend.identidad.infrastructure.security.JwtAuthenticationFilter;
import cl.casol.backend.identidad.infrastructure.web.AuthController;
import cl.casol.backend.shared.infrastructure.security.SecurityConfig;
import cl.casol.backend.shared.infrastructure.web.GlobalExceptionHandler;
import cl.casol.backend.shared.infrastructure.web.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, HealthController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class Hu01SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AutenticarUsuarioService autenticarUsuarioService;

    @MockitoBean
    private TokenServicePort tokenService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @Test
    void loginCorrectoResponde200() throws Exception {
        Usuario usuario = usuario(true);
        when(autenticarUsuarioService.autenticar("user@example.com", "password"))
                .thenReturn(usuario);
        when(tokenService.generarToken(usuario)).thenReturn("jwt-generado");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-generado"));
    }

    @Test
    void credencialesIncorrectasResponden401() throws Exception {
        when(autenticarUsuarioService.autenticar(anyString(), anyString()))
                .thenThrow(new CredencialesInvalidasException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioInactivoResponde403() throws Exception {
        when(autenticarUsuarioService.autenticar(anyString(), anyString()))
                .thenThrow(new UsuarioInactivoException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestInvalidoResponde400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void endpointProtegidoSinJwtResponde401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtInvalidoOExpiradoResponde401() throws Exception {
        when(tokenService.esValido("jwt-invalido")).thenReturn(false);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer jwt-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtValidoDeUsuarioActivoAutentica() throws Exception {
        when(tokenService.esValido("jwt-valido")).thenReturn(true);
        when(tokenService.obtenerEmail("jwt-valido")).thenReturn("user@example.com");
        when(usuarioRepository.buscarPorEmail("user@example.com"))
                .thenReturn(Optional.of(usuario(true)));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer jwt-valido"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void endpointsPublicosNoRequierenJwt() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    private Usuario usuario(boolean activo) {
        Rol rol = new Rol(1, "USUARIO", null, true, null, null);
        return new Usuario(1, rol, "Usuario", "user@example.com", "hash-bcrypt",
                activo, null, null);
    }
}
