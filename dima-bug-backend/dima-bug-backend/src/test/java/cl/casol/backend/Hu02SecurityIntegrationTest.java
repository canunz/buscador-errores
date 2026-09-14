package cl.casol.backend;

import cl.casol.backend.identidad.application.port.out.TokenServicePort;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.application.service.AdministrarUsuariosService;
import cl.casol.backend.identidad.application.service.ListarRolesService;
import cl.casol.backend.identidad.domain.Rol;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.domain.exception.EmailDuplicadoException;
import cl.casol.backend.identidad.domain.exception.AutoDesactivacionException;
import cl.casol.backend.identidad.domain.exception.UsuarioNoEncontradoException;
import cl.casol.backend.identidad.infrastructure.security.JwtAuthenticationFilter;
import cl.casol.backend.identidad.infrastructure.web.RolController;
import cl.casol.backend.identidad.infrastructure.web.UsuarioController;
import cl.casol.backend.shared.infrastructure.security.SecurityConfig;
import cl.casol.backend.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {UsuarioController.class, RolController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class Hu02SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdministrarUsuariosService usuariosService;

    @MockitoBean
    private ListarRolesService rolesService;

    @MockitoBean
    private TokenServicePort tokenService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @Test
    void administradorPuedeListarUsuarios() throws Exception {
        autenticarComo("jwt-admin", "admin@dimarsa.cl", "ADMINISTRADOR");
        when(usuariosService.listar()).thenReturn(List.of(usuario(2, "tecnico@dimarsa.cl", "TECNICO", true)));

        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer jwt-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("tecnico@dimarsa.cl"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("password"))));
    }

    @Test
    void tecnicoRecibe403() throws Exception {
        autenticarComo("jwt-tecnico", "tecnico@dimarsa.cl", "TECNICO");

        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer jwt-tecnico"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinJwtRecibe401() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administradorPuedeCrearSinExponerPassword() throws Exception {
        autenticarComo("jwt-admin", "admin@dimarsa.cl", "ADMINISTRADOR");
        when(usuariosService.crear("Nuevo", "nuevo@dimarsa.cl", "ClaveInicialSegura", 2))
                .thenReturn(usuario(3, "nuevo@dimarsa.cl", "TECNICO", true));

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer jwt-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nuevo\",\"email\":\"nuevo@dimarsa.cl\","
                                + "\"password\":\"ClaveInicialSegura\",\"rolId\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nuevo@dimarsa.cl"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("password"))));
    }

    @Test
    void emailInvalidoResponde400() throws Exception {
        autenticarComo("jwt-admin", "admin@dimarsa.cl", "ADMINISTRADOR");

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer jwt-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nuevo\",\"email\":\"invalido\","
                                + "\"password\":\"clave\",\"rolId\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emailDuplicadoResponde409() throws Exception {
        autenticarComo("jwt-admin", "admin@dimarsa.cl", "ADMINISTRADOR");
        when(usuariosService.crear(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new EmailDuplicadoException());

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer jwt-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nuevo\",\"email\":\"usado@dimarsa.cl\","
                                + "\"password\":\"clave\",\"rolId\":2}"))
                .andExpect(status().isConflict());
    }

    @Test
    void administradorPuedeModificarYCambiarEstado() throws Exception {
        autenticarComo("jwt-admin", "admin@dimarsa.cl", "ADMINISTRADOR");
        Usuario modificado = usuario(2, "nuevo@dimarsa.cl", "ADMINISTRADOR", true);
        Usuario desactivado = usuario(2, "nuevo@dimarsa.cl", "ADMINISTRADOR", false);
        when(usuariosService.modificar(2, "Nuevo nombre", "nuevo@dimarsa.cl", 1)).thenReturn(modificado);
        when(usuariosService.cambiarEstado(2, false, "admin@dimarsa.cl")).thenReturn(desactivado);

        mockMvc.perform(put("/api/usuarios/2")
                        .header("Authorization", "Bearer jwt-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nuevo nombre\",\"email\":\"nuevo@dimarsa.cl\",\"rolId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"));

        mockMvc.perform(patch("/api/usuarios/2/estado")
                        .header("Authorization", "Bearer jwt-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void rolesDevuelveCatalogoActivoSinCamposInternos() throws Exception {
        autenticarComo("jwt-admin", "admin@dimarsa.cl", "ADMINISTRADOR");
        when(rolesService.listarActivos()).thenReturn(List.of(
                new Rol(1, "ADMINISTRADOR", "Administración", true, null, null)));

        mockMvc.perform(get("/api/roles").header("Authorization", "Bearer jwt-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$[0].descripcion").value("Administración"))
                .andExpect(jsonPath("$[0].activo").doesNotExist());
    }

    @Test
    void usuarioInexistenteResponde404() throws Exception {
        autenticarComo("jwt-admin", "admin@dimarsa.cl", "ADMINISTRADOR");
        when(usuariosService.buscarPorId(404)).thenThrow(new UsuarioNoEncontradoException());

        mockMvc.perform(get("/api/usuarios/404").header("Authorization", "Bearer jwt-admin"))
                .andExpect(status().isNotFound());
    }

    @Test
    void autoDesactivacionResponde400() throws Exception {
        autenticarComo("jwt-admin", "admin@dimarsa.cl", "ADMINISTRADOR");
        when(usuariosService.cambiarEstado(1, false, "admin@dimarsa.cl"))
                .thenThrow(new AutoDesactivacionException());

        mockMvc.perform(patch("/api/usuarios/1/estado")
                        .header("Authorization", "Bearer jwt-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isBadRequest());
    }

    private void autenticarComo(String token, String email, String rol) {
        when(tokenService.esValido(token)).thenReturn(true);
        when(tokenService.obtenerEmail(token)).thenReturn(email);
        when(usuarioRepository.buscarPorEmail(email)).thenReturn(Optional.of(usuario(1, email, rol, true)));
    }

    private Usuario usuario(int id, String email, String rolNombre, boolean activo) {
        Rol rol = new Rol(rolNombre.equals("ADMINISTRADOR") ? 1 : 2, rolNombre, null, true, null, null);
        return new Usuario(id, rol, "Usuario", email, "hash-no-expuesto", activo, null, null);
    }
}
