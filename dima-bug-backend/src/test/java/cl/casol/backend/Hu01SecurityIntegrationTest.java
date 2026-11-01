package cl.casol.backend;

import cl.casol.backend.identidad.application.port.out.TokenServicePort;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.application.service.AutenticarUsuarioService;
import cl.casol.backend.identidad.domain.Rol;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.application.service.SolicitudCodigo;
import cl.casol.backend.identidad.domain.exception.CodigoAccesoInvalidoException;
import cl.casol.backend.identidad.domain.exception.CorreoNoRegistradoException;
import cl.casol.backend.identidad.domain.exception.ReenvioCodigoAnticipadoException;
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
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private cl.casol.backend.identidad.infrastructure.security.UsuarioAutenticadoCache usuarioAutenticadoCache;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private cl.casol.backend.shared.application.service.VisibilidadContenidoService visibilidadContenidoService;


    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AutenticarUsuarioService autenticarUsuarioService;

    @MockitoBean
    private TokenServicePort tokenService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @Test
    void solicitarCodigoResponde200ConTiempos() throws Exception {
        when(autenticarUsuarioService.solicitarCodigo("user@example.com"))
                .thenReturn(new SolicitudCodigo(300, 60));

        mockMvc.perform(post("/api/auth/codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segundosVigencia").value(300))
                .andExpect(jsonPath("$.segundosReenvio").value(60));
    }

    @Test
    void correoNoRegistradoResponde404() throws Exception {
        when(autenticarUsuarioService.solicitarCodigo(anyString()))
                .thenThrow(new CorreoNoRegistradoException());

        mockMvc.perform(post("/api/auth/codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nadie@example.com\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Este correo no está registrado en DimaBug. Inténtelo con un correo que exista."));
    }

    @Test
    void sinFormatoDeCorreoResponde400() throws Exception {
        mockMvc.perform(post("/api/auth/codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"usuario\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reenviarAntesDeTiempoResponde429() throws Exception {
        when(autenticarUsuarioService.solicitarCodigo(anyString()))
                .thenThrow(new ReenvioCodigoAnticipadoException(40));

        mockMvc.perform(post("/api/auth/codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.segundosRestantes").value(40));
    }

    @Test
    void codigoCorrectoEntregaJwt() throws Exception {
        Usuario usuario = usuario(true);
        when(autenticarUsuarioService.verificarCodigo("user@example.com", "123456"))
                .thenReturn(usuario);
        when(tokenService.generarToken(usuario)).thenReturn("jwt-generado");

        mockMvc.perform(post("/api/auth/codigo/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"codigo\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-generado"));
    }

    @Test
    void codigoIncorrectoOVencidoResponde401() throws Exception {
        when(autenticarUsuarioService.verificarCodigo(anyString(), anyString()))
                .thenThrow(new CodigoAccesoInvalidoException());

        mockMvc.perform(post("/api/auth/codigo/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"codigo\":\"000000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioInactivoResponde403() throws Exception {
        when(autenticarUsuarioService.verificarCodigo(anyString(), anyString()))
                .thenThrow(new UsuarioInactivoException());

        mockMvc.perform(post("/api/auth/codigo/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"codigo\":\"123456\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestInvalidoResponde400() throws Exception {
        mockMvc.perform(post("/api/auth/codigo/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"codigo\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void loginConContrasenaYaNoExiste() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isUnauthorized());
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
