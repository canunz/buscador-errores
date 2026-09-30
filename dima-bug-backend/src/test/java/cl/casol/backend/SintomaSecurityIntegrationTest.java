package cl.casol.backend;

import cl.casol.backend.conocimiento.application.service.MantenerSintomaService;
import cl.casol.backend.conocimiento.domain.Sintoma;
import cl.casol.backend.conocimiento.domain.exception.ConocimientoNoEncontradoException;
import cl.casol.backend.conocimiento.domain.exception.SintomaNoEncontradoException;
import cl.casol.backend.conocimiento.infrastructure.web.SintomaController;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SintomaController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class SintomaSecurityIntegrationTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean MantenerSintomaService service;
    @MockitoBean TokenServicePort tokenService;
    @MockitoBean UsuarioRepository usuarioRepository;

    @Test void getValidoConSintomasDevuelve200OrdenadoYDto() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.listar(4)).thenReturn(List.of(
                new Sintoma(2, 4, "Primero", 1), new Sintoma(1, 4, "Segundo", 2)));
        mockMvc.perform(get("/api/conocimientos/4/sintomas").header("Authorization", "Bearer jwt-tecnico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].descripcion").value("Primero"))
                .andExpect(jsonPath("$[0].orden").value(1))
                .andExpect(jsonPath("$[1].orden").value(2))
                .andExpect(jsonPath("$[0].conocimientoId").doesNotExist());
    }

    @Test void getValidoSinSintomasDevuelve200Vacio() throws Exception {
        autenticarComo("jwt-admin", "ADMINISTRADOR");
        when(service.listar(4)).thenReturn(List.of());
        mockMvc.perform(get("/api/conocimientos/4/sintomas").header("Authorization", "Bearer jwt-admin"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void getConocimientoInexistenteDevuelve404() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.listar(404)).thenThrow(new ConocimientoNoEncontradoException(404));
        mockMvc.perform(get("/api/conocimientos/404/sintomas").header("Authorization", "Bearer jwt-tecnico"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").exists());
    }

    @Test void postValidoDevuelve201() throws Exception {
        autenticarComo("jwt-admin", "ADMINISTRADOR");
        when(service.crear(4, "Mensaje", 1)).thenReturn(new Sintoma(9, 4, "Mensaje", 1));
        mockMvc.perform(post("/api/conocimientos/4/sintomas").header("Authorization", "Bearer jwt-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Mensaje\",\"orden\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(9));
    }

    @Test void postConocimientoInexistenteDevuelve404() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.crear(404, "Mensaje", 1)).thenThrow(new ConocimientoNoEncontradoException(404));
        mockMvc.perform(post("/api/conocimientos/404/sintomas").header("Authorization", "Bearer jwt-tecnico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Mensaje\",\"orden\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test void postDescripcionVaciaDevuelve400() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        mockMvc.perform(post("/api/conocimientos/4/sintomas").header("Authorization", "Bearer jwt-tecnico")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"descripcion\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void postDescripcionMayorA300Devuelve400() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        String body = "{\"descripcion\":\"" + "a".repeat(301) + "\"}";
        mockMvc.perform(post("/api/conocimientos/4/sintomas").header("Authorization", "Bearer jwt-tecnico")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test void putValidoDevuelve200() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.modificar(4, 9, "Nuevo", 2)).thenReturn(new Sintoma(9, 4, "Nuevo", 2));
        mockMvc.perform(put("/api/conocimientos/4/sintomas/9").header("Authorization", "Bearer jwt-tecnico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Nuevo\",\"orden\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.descripcion").value("Nuevo"))
                .andExpect(jsonPath("$.orden").value(2));
    }

    @Test void putSintomaInexistenteDevuelve404() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.modificar(4, 99, "Nuevo", 2)).thenThrow(new SintomaNoEncontradoException(99));
        mockMvc.perform(put("/api/conocimientos/4/sintomas/99").header("Authorization", "Bearer jwt-tecnico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Nuevo\",\"orden\":2}"))
                .andExpect(status().isNotFound());
    }

    @Test void putSintomaDeOtroConocimientoDevuelve404() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.modificar(4, 9, "Nuevo", 2)).thenThrow(new SintomaNoEncontradoException(9));
        mockMvc.perform(put("/api/conocimientos/4/sintomas/9").header("Authorization", "Bearer jwt-tecnico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Nuevo\",\"orden\":2}"))
                .andExpect(status().isNotFound());
    }

    @Test void putRequestInvalidoDevuelve400() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        mockMvc.perform(put("/api/conocimientos/4/sintomas/9").header("Authorization", "Bearer jwt-tecnico")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"descripcion\":null,\"orden\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test void sinJwtDevuelve401EnTodosLosEndpoints() throws Exception {
        mockMvc.perform(get("/api/conocimientos/4/sintomas")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/conocimientos/4/sintomas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Mensaje\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/conocimientos/4/sintomas/9").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Mensaje\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void rolNoAutorizadoDevuelve403() throws Exception {
        autenticarComo("jwt-auditor", "AUDITOR");
        mockMvc.perform(get("/api/conocimientos/4/sintomas").header("Authorization", "Bearer jwt-auditor"))
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
