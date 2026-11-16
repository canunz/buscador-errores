package cl.casol.backend;

import cl.casol.backend.identidad.application.port.out.TokenServicePort;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.application.service.AutenticarUsuarioService;
import cl.casol.backend.identidad.domain.Rol;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.application.service.SolicitudCodigo;
import cl.casol.backend.identidad.domain.exception.CodigoAccesoInvalidoException;
import cl.casol.backend.identidad.domain.exception.CorreoNoRegistradoException;
import cl.casol.backend.identidad.domain.exception.CredencialesInvalidasException;
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
    void solicitarCodigoResponde202ConTiempos() throws Exception {
        when(autenticarUsuarioService.solicitarCodigo("user@example.com"))
                .thenReturn(new SolicitudCodigo(300, 60));

        mockMvc.perform(post("/api/auth/codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.segundosVigencia").value(300))
                .andExpect(jsonPath("$.segundosReenvio").value(60));
    }

    @Test
    void correoNoRegistradoRecibeRespuestaUniforme() throws Exception {
        when(autenticarUsuarioService.solicitarCodigo(anyString()))
                .thenReturn(new SolicitudCodigo(300,60));

        mockMvc.perform(post("/api/auth/codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nadie@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Si la cuenta está habilitada, recibirá un código. Revise también spam."));
    }

    @Test
    void sinFormatoDeCorreoResponde400() throws Exception {
        mockMvc.perform(post("/api/auth/codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"usuario\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reenvioRecibeRespuestaUniforme() throws Exception {
        when(autenticarUsuarioService.solicitarCodigo(anyString()))
                .thenReturn(new SolicitudCodigo(300,60));

        mockMvc.perform(post("/api/auth/codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.segundosReenvio").value(60));
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
    void usuarioInactivoNoRevelaEstado() throws Exception {
        when(autenticarUsuarioService.verificarCodigo(anyString(), anyString()))
                .thenThrow(new CodigoAccesoInvalidoException());

        mockMvc.perform(post("/api/auth/codigo/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"codigo\":\"123456\"}"))
                .andExpect(status().isUnauthorized());
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
    void loginConPasswordEstaRetirado() throws Exception {
        Rol rol = new Rol(2, "ADMINISTRADOR", null, true, null, null);
        Usuario admin = new Usuario(2, rol, "Admin", "admin@example.com", "hash-bcrypt", true, null, null);
        when(autenticarUsuarioService.autenticar("admin@example.com", "password")).thenReturn(admin);
        when(tokenService.generarToken(admin)).thenReturn("jwt-admin");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isGone());
    }

    @Test
    void passwordRetiradaNoRevelaCuenta() throws Exception {
        when(autenticarUsuarioService.autenticar(anyString(), anyString()))
                .thenThrow(new CredencialesInvalidasException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isGone());
    }

    @Test
    void recuperarPasswordEsPublico() throws Exception {
        when(autenticarUsuarioService.solicitarCodigo("admin@example.com"))
                .thenReturn(new SolicitudCodigo(300, 4));

        mockMvc.perform(post("/api/auth/recuperar/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.segundosVigencia").value(300));
    }

    @Test
    void administradorRecibeModoCodigo() throws Exception {
        when(autenticarUsuarioService.solicitarCodigo("admin@example.com"))
                .thenReturn(new SolicitudCodigo(300,60));

        mockMvc.perform(post("/api/auth/codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.modo").value("CODIGO"));
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
