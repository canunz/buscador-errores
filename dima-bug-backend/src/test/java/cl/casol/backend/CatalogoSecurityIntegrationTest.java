package cl.casol.backend;

import cl.casol.backend.catalogo.application.service.ConsultarHardwareService;
import cl.casol.backend.catalogo.application.service.ConsultarSistemasService;
import cl.casol.backend.catalogo.domain.Hardware;
import cl.casol.backend.catalogo.domain.Modulo;
import cl.casol.backend.catalogo.domain.Sistema;
import cl.casol.backend.catalogo.domain.exception.CatalogoNoEncontradoException;
import cl.casol.backend.catalogo.infrastructure.web.HardwareController;
import cl.casol.backend.catalogo.infrastructure.web.SistemaController;
import cl.casol.backend.identidad.application.port.out.TokenServicePort;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.Rol;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.infrastructure.security.JwtAuthenticationFilter;
import cl.casol.backend.shared.infrastructure.security.SecurityConfig;
import cl.casol.backend.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {SistemaController.class, HardwareController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class CatalogoSecurityIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ConsultarSistemasService sistemasService;
    @MockitoBean private ConsultarHardwareService hardwareService;
    @MockitoBean private TokenServicePort tokenService;
    @MockitoBean private UsuarioRepository usuarioRepository;

    @Test
    void sinJwtNoPuedeConsultarCatalogos() throws Exception {
        mockMvc.perform(get("/api/sistemas")).andExpect(status().isUnauthorized());
    }

    @Test
    void tecnicoPuedeConsultarSistemaYModulos() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(sistemasService.buscar(1)).thenReturn(new Sistema(1, "Jadima", "ERP", true));
        when(sistemasService.listarModulos(1)).thenReturn(List.of(new Modulo(4, 1, "Ventas", true)));

        mockMvc.perform(get("/api/sistemas/1").header("Authorization", "Bearer jwt-tecnico"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Jadima"))
                .andExpect(jsonPath("$.activo").doesNotExist());
        mockMvc.perform(get("/api/sistemas/1/modulos").header("Authorization", "Bearer jwt-tecnico"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nombre").value("Ventas"));
    }

    @Test
    void administradorPuedeNavegarRelacionEnAmbosSentidos() throws Exception {
        autenticarComo("jwt-admin", "ADMINISTRADOR");
        when(hardwareService.listarSistemas(7)).thenReturn(List.of(new Sistema(1, "Jadima", null, true)));
        when(sistemasService.listarHardware(1)).thenReturn(List.of(new Hardware(7, "PC", "Windows", true)));

        mockMvc.perform(get("/api/hardware/7/sistemas").header("Authorization", "Bearer jwt-admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nombre").value("Jadima"));
        mockMvc.perform(get("/api/sistemas/1/hardware").header("Authorization", "Bearer jwt-admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nombre").value("PC"));
    }

    @Test
    void otroRolNoPuedeConsultarCatalogos() throws Exception {
        autenticarComo("jwt-otro", "AUDITOR");
        mockMvc.perform(get("/api/hardware").header("Authorization", "Bearer jwt-otro"))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogoInexistenteResponde404() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(hardwareService.buscar(404)).thenThrow(new CatalogoNoEncontradoException("Hardware", 404));
        mockMvc.perform(get("/api/hardware/404").header("Authorization", "Bearer jwt-tecnico"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").exists());
    }

    private void autenticarComo(String token, String rolNombre) {
        String email = rolNombre.toLowerCase() + "@dimarsa.cl";
        when(tokenService.esValido(token)).thenReturn(true);
        when(tokenService.obtenerEmail(token)).thenReturn(email);
        Rol rol = new Rol(1, rolNombre, null, true, null, null);
        Usuario usuario = new Usuario(1, rol, "Usuario", email, "hash", true, null, null);
        when(usuarioRepository.buscarPorEmail(email)).thenReturn(Optional.of(usuario));
    }
}
