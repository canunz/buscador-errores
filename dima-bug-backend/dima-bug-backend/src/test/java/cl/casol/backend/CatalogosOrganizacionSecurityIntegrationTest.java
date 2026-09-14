package cl.casol.backend;

import cl.casol.backend.catalogo.application.service.ConsultarFrecuenciasService;
import cl.casol.backend.catalogo.domain.Frecuencia;
import cl.casol.backend.catalogo.infrastructure.web.FrecuenciaController;
import cl.casol.backend.identidad.application.port.out.TokenServicePort;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.application.service.ConsultarOrganizacionService;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.domain.exception.DepartamentoNoEncontradoException;
import cl.casol.backend.identidad.infrastructure.security.JwtAuthenticationFilter;
import cl.casol.backend.identidad.infrastructure.web.DepartamentoController;
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

@WebMvcTest(controllers = {FrecuenciaController.class, DepartamentoController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class CatalogosOrganizacionSecurityIntegrationTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ConsultarFrecuenciasService frecuenciasService;
    @MockitoBean ConsultarOrganizacionService organizacionService;
    @MockitoBean TokenServicePort tokenService;
    @MockitoBean UsuarioRepository usuarioRepository;

    @Test
    void frecuenciasAutenticadoDevuelve200SoloConDtoActivos() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(frecuenciasService.listar()).thenReturn(List.of(new Frecuencia(1, "Diario", true)));
        mockMvc.perform(get("/api/frecuencias").header("Authorization", "Bearer jwt-tecnico"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Diario"))
                .andExpect(jsonPath("$[0].activo").doesNotExist());
    }

    @Test void frecuenciasSinJwtDevuelve401() throws Exception {
        mockMvc.perform(get("/api/frecuencias")).andExpect(status().isUnauthorized());
    }

    @Test
    void departamentosAutenticadoDevuelve200SoloConDtoActivos() throws Exception {
        autenticarComo("jwt-admin", "Administrador");
        when(organizacionService.listarDepartamentos())
                .thenReturn(List.of(new Departamento(2, "Desarrollo", true)));
        mockMvc.perform(get("/api/departamentos").header("Authorization", "Bearer jwt-admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nombre").value("Desarrollo"))
                .andExpect(jsonPath("$[0].activo").doesNotExist());
    }

    @Test void departamentosSinJwtDevuelve401() throws Exception {
        mockMvc.perform(get("/api/departamentos")).andExpect(status().isUnauthorized());
    }

    @Test
    void responsablesDepartamentoValidoDevuelve200SoloConDtoActivos() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(organizacionService.listarResponsables(1)).thenReturn(List.of(
                new Responsable(3, 1, "Carolina Cheuquepil", "Soporte TI", "contacto", true)));
        mockMvc.perform(get("/api/departamentos/1/responsables")
                        .header("Authorization", "Bearer jwt-tecnico"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nombre").value("Carolina Cheuquepil"))
                .andExpect(jsonPath("$[0].cargo").value("Soporte TI"))
                .andExpect(jsonPath("$[0].departamentoId").doesNotExist())
                .andExpect(jsonPath("$[0].activo").doesNotExist());
    }

    @Test
    void departamentoValidoSinResponsablesDevuelve200YListaVacia() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(organizacionService.listarResponsables(1)).thenReturn(List.of());
        mockMvc.perform(get("/api/departamentos/1/responsables")
                        .header("Authorization", "Bearer jwt-tecnico"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void departamentoInexistenteOInactivoDevuelve404() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(organizacionService.listarResponsables(99)).thenThrow(new DepartamentoNoEncontradoException(99));
        mockMvc.perform(get("/api/departamentos/99/responsables")
                        .header("Authorization", "Bearer jwt-tecnico"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").exists());
    }

    @Test void responsablesSinJwtDevuelve401() throws Exception {
        mockMvc.perform(get("/api/departamentos/1/responsables")).andExpect(status().isUnauthorized());
    }

    @Test
    void rolNoAutorizadoDevuelve403EnLosTresRecursos() throws Exception {
        autenticarComo("jwt-auditor", "AUDITOR");
        String authorization = "Bearer jwt-auditor";
        mockMvc.perform(get("/api/frecuencias").header("Authorization", authorization)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/departamentos").header("Authorization", authorization)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/departamentos/1/responsables").header("Authorization", authorization))
                .andExpect(status().isForbidden());
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
