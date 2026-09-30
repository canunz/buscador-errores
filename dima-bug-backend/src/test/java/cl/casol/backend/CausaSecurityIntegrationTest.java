package cl.casol.backend;

import cl.casol.backend.conocimiento.application.service.MantenerCausaService;
import cl.casol.backend.conocimiento.domain.Causa;
import cl.casol.backend.conocimiento.domain.exception.CausaNoEncontradaException;
import cl.casol.backend.conocimiento.domain.exception.ConocimientoNoEncontradoException;
import cl.casol.backend.conocimiento.infrastructure.web.CausaController;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CausaController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class CausaSecurityIntegrationTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean MantenerCausaService service;
    @MockitoBean TokenServicePort tokenService;
    @MockitoBean UsuarioRepository usuarioRepository;

    @Test void getValidoConCausasDevuelve200OrdenadoYDto() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.listar(4)).thenReturn(List.of(
                new Causa(2, 4, "Cola bloqueada", 1), new Causa(1, 4, "Fuera de línea", 2)));
        mockMvc.perform(get("/api/conocimientos/4/causas").header("Authorization", "Bearer jwt-tecnico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].descripcion").value("Cola bloqueada"))
                .andExpect(jsonPath("$[0].orden").value(1))
                .andExpect(jsonPath("$[1].orden").value(2))
                .andExpect(jsonPath("$[0].conocimientoId").doesNotExist());
    }

    @Test void getValidoSinCausasDevuelve200VacioYPermiteAdministrador() throws Exception {
        autenticarComo("jwt-admin", "ADMINISTRADOR");
        when(service.listar(4)).thenReturn(List.of());
        mockMvc.perform(get("/api/conocimientos/4/causas").header("Authorization", "Bearer jwt-admin"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void getConocimientoInexistenteDevuelve404() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.listar(404)).thenThrow(new ConocimientoNoEncontradoException(404));
        mockMvc.perform(get("/api/conocimientos/404/causas").header("Authorization", "Bearer jwt-tecnico"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").exists());
    }

    @Test void postValidoDevuelve201() throws Exception {
        autenticarComo("jwt-admin", "ADMINISTRADOR");
        when(service.crear(4, "Cola bloqueada", 1)).thenReturn(new Causa(9, 4, "Cola bloqueada", 1));
        mockMvc.perform(post("/api/conocimientos/4/causas").header("Authorization", "Bearer jwt-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Cola bloqueada\",\"orden\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(9));
    }

    @Test void postConOrdenOmitidoUsaUno() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.crear(4, "Sin conectividad", null)).thenReturn(new Causa(10, 4, "Sin conectividad", 1));
        mockMvc.perform(post("/api/conocimientos/4/causas").header("Authorization", "Bearer jwt-tecnico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Sin conectividad\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.orden").value(1));
    }

    @Test void postConocimientoInexistenteDevuelve404() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.crear(404, "Cola bloqueada", 1)).thenThrow(new ConocimientoNoEncontradoException(404));
        mockMvc.perform(post("/api/conocimientos/404/causas").header("Authorization", "Bearer jwt-tecnico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Cola bloqueada\",\"orden\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test void postDescripcionVaciaDevuelve400() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        mockMvc.perform(post("/api/conocimientos/4/causas").header("Authorization", "Bearer jwt-tecnico")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"descripcion\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void postDescripcionMayorA300Devuelve400() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        String body = "{\"descripcion\":\"" + "a".repeat(301) + "\"}";
        mockMvc.perform(post("/api/conocimientos/4/causas").header("Authorization", "Bearer jwt-tecnico")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test void putValidoDevuelve200() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.modificar(4, 9, "Impresora sin conectividad", 2))
                .thenReturn(new Causa(9, 4, "Impresora sin conectividad", 2));
        mockMvc.perform(put("/api/conocimientos/4/causas/9").header("Authorization", "Bearer jwt-tecnico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Impresora sin conectividad\",\"orden\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value("Impresora sin conectividad"))
                .andExpect(jsonPath("$.orden").value(2));
    }

    @Test void putCausaInexistenteDevuelve404() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.modificar(4, 99, "Nueva", 2)).thenThrow(new CausaNoEncontradaException(99));
        mockMvc.perform(put("/api/conocimientos/4/causas/99").header("Authorization", "Bearer jwt-tecnico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Nueva\",\"orden\":2}"))
                .andExpect(status().isNotFound());
    }

    @Test void putCausaDeOtroConocimientoDevuelve404() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.modificar(4, 9, "Nueva", 2)).thenThrow(new CausaNoEncontradaException(9));
        mockMvc.perform(put("/api/conocimientos/4/causas/9").header("Authorization", "Bearer jwt-tecnico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Nueva\",\"orden\":2}"))
                .andExpect(status().isNotFound());
    }

    @Test void putRequestInvalidoDevuelve400() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        mockMvc.perform(put("/api/conocimientos/4/causas/9").header("Authorization", "Bearer jwt-tecnico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":null,\"orden\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test void sinJwtDevuelve401EnTodosLosEndpoints() throws Exception {
        mockMvc.perform(get("/api/conocimientos/4/causas")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/conocimientos/4/causas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Causa\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/conocimientos/4/causas/9").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Causa\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void rolNoAutorizadoDevuelve403() throws Exception {
        autenticarComo("jwt-auditor", "AUDITOR");
        mockMvc.perform(get("/api/conocimientos/4/causas").header("Authorization", "Bearer jwt-auditor"))
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
