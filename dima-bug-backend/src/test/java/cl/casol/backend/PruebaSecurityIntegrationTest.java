package cl.casol.backend;

import cl.casol.backend.conocimiento.application.service.GestionarPruebasConocimientoService;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import cl.casol.backend.conocimiento.infrastructure.web.ConocimientoPruebaController;
import cl.casol.backend.conocimiento.infrastructure.web.PruebaController;
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

@WebMvcTest(controllers = {PruebaController.class, ConocimientoPruebaController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class PruebaSecurityIntegrationTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean GestionarPruebasConocimientoService service;
    @MockitoBean TokenServicePort tokenService;
    @MockitoBean UsuarioRepository usuarioRepository;

    @Test void catalogoDevuelveSoloDtoActivoEnOrdenAlfabeticoYTecnicoPermitido() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.listarCatalogo()).thenReturn(List.of(
                new Prueba(2, "Comprobar cable", null, true),
                new Prueba(1, "Hacer ping", "Responde", true)));
        mockMvc.perform(get("/api/pruebas").header("Authorization", "Bearer jwt-tecnico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].descripcion").value("Comprobar cable"))
                .andExpect(jsonPath("$[1].descripcion").value("Hacer ping"))
                .andExpect(jsonPath("$[1].resultadoEsperado").value("Responde"))
                .andExpect(jsonPath("$[0].activa").doesNotExist());
    }

    @Test void catalogoPermiteAdministrador() throws Exception {
        autenticarComo("jwt-admin", "ADMINISTRADOR");
        when(service.listarCatalogo()).thenReturn(List.of());
        mockMvc.perform(get("/api/pruebas").header("Authorization", "Bearer jwt-admin"))
                .andExpect(status().isOk());
    }

    @Test void catalogoSinJwtDevuelve401() throws Exception {
        mockMvc.perform(get("/api/pruebas")).andExpect(status().isUnauthorized());
    }

    @Test void catalogoRolNoAutorizadoDevuelve403() throws Exception {
        autenticarComo("jwt-auditor", "AUDITOR");
        mockMvc.perform(get("/api/pruebas").header("Authorization", "Bearer jwt-auditor"))
                .andExpect(status().isForbidden());
    }

    @Test void conocimientoValidoConPruebasActivasDevuelve200Ordenado() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        Prueba ping = new Prueba(1, "Hacer ping", "Responde", true);
        when(service.listarPorConocimiento(4)).thenReturn(List.of(
                new ConocimientoPrueba(4, ping, 1),
                new ConocimientoPrueba(4, new Prueba(2, "Traceroute", null, true), 2)));
        mockMvc.perform(get("/api/conocimientos/4/pruebas").header("Authorization", "Bearer jwt-tecnico"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].orden").value(1)).andExpect(jsonPath("$[1].orden").value(2))
                .andExpect(jsonPath("$[0].conocimientoId").doesNotExist())
                .andExpect(jsonPath("$[0].activa").doesNotExist());
    }

    @Test void conocimientoSinPruebasDevuelve200Vacio() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.listarPorConocimiento(4)).thenReturn(List.of());
        mockMvc.perform(get("/api/conocimientos/4/pruebas").header("Authorization", "Bearer jwt-tecnico"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void conocimientoInexistenteAlListarDevuelve404() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.listarPorConocimiento(404)).thenThrow(new ConocimientoNoEncontradoException(404));
        mockMvc.perform(get("/api/conocimientos/404/pruebas").header("Authorization", "Bearer jwt-tecnico"))
                .andExpect(status().isNotFound());
    }

    @Test void asociacionValidaDevuelve201() throws Exception {
        autenticarComo("jwt-admin", "ADMINISTRADOR");
        Prueba prueba = new Prueba(3, "Hacer ping", "Responde", true);
        when(service.asociar(4, 3, 2)).thenReturn(new ConocimientoPrueba(4, prueba, 2));
        mockMvc.perform(post("/api/conocimientos/4/pruebas").header("Authorization", "Bearer jwt-admin")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pruebaId\":3,\"orden\":2}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.orden").value(2));
    }

    @Test void asociacionConOrdenOmitidoUsaUno() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        Prueba prueba = new Prueba(3, "Hacer ping", "Responde", true);
        when(service.asociar(4, 3, null)).thenReturn(new ConocimientoPrueba(4, prueba, 1));
        mockMvc.perform(post("/api/conocimientos/4/pruebas").header("Authorization", "Bearer jwt-tecnico")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pruebaId\":3}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.orden").value(1));
    }

    @Test void asociarConocimientoInexistenteDevuelve404() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.asociar(404, 3, 1)).thenThrow(new ConocimientoNoEncontradoException(404));
        postAsociacion(404, "{\"pruebaId\":3,\"orden\":1}", "jwt-tecnico", 404);
    }

    @Test void asociarPruebaInexistenteDevuelve404() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.asociar(4, 99, 1)).thenThrow(new PruebaNoEncontradaException(99));
        postAsociacion(4, "{\"pruebaId\":99,\"orden\":1}", "jwt-tecnico", 404);
    }

    @Test void asociarPruebaInactivaDevuelve404() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.asociar(4, 98, 1)).thenThrow(new PruebaNoEncontradaException(98));
        postAsociacion(4, "{\"pruebaId\":98,\"orden\":1}", "jwt-tecnico", 404);
    }

    @Test void asociacionDuplicadaDevuelve409() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.asociar(4, 3, 1)).thenThrow(new PruebaYaAsociadaException(4, 3));
        postAsociacion(4, "{\"pruebaId\":3,\"orden\":1}", "jwt-tecnico", 409);
    }

    @Test void asociacionConDatosInvalidosDevuelve400() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        postAsociacion(4, "{\"pruebaId\":0,\"orden\":0}", "jwt-tecnico", 400);
    }

    @Test void actualizacionOrdenValidaDevuelve200() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        Prueba prueba = new Prueba(3, "Hacer ping", "Responde", true);
        when(service.actualizarOrden(4, 3, 2)).thenReturn(new ConocimientoPrueba(4, prueba, 2));
        mockMvc.perform(put("/api/conocimientos/4/pruebas/3").header("Authorization", "Bearer jwt-tecnico")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orden\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orden").value(2));
    }

    @Test void actualizarConocimientoInexistenteDevuelve404() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.actualizarOrden(404, 3, 2)).thenThrow(new ConocimientoNoEncontradoException(404));
        putOrden(404, 3, 2, 404);
    }

    @Test void actualizarAsociacionInexistenteDevuelve404() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        when(service.actualizarOrden(4, 99, 2)).thenThrow(new AsociacionPruebaNoEncontradaException(4, 99));
        putOrden(4, 99, 2, 404);
    }

    @Test void actualizarConOrdenInvalidoDevuelve400() throws Exception {
        autenticarComo("jwt-tecnico", "TECNICO");
        putOrden(4, 3, 0, 400);
    }

    @Test void endpointsDeAsociacionSinJwtDevuelven401() throws Exception {
        mockMvc.perform(get("/api/conocimientos/4/pruebas")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/conocimientos/4/pruebas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pruebaId\":3}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/conocimientos/4/pruebas/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orden\":2}"))
                .andExpect(status().isUnauthorized());
    }

    private void postAsociacion(int conocimientoId, String body, String token, int status) throws Exception {
        mockMvc.perform(post("/api/conocimientos/{id}/pruebas", conocimientoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(status));
    }

    private void putOrden(int conocimientoId, int pruebaId, int orden, int status) throws Exception {
        mockMvc.perform(put("/api/conocimientos/{id}/pruebas/{pruebaId}", conocimientoId, pruebaId)
                        .header("Authorization", "Bearer jwt-tecnico")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orden\":" + orden + "}"))
                .andExpect(status().is(status));
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
