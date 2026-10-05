package cl.casol.backend;

import cl.casol.backend.conocimiento.application.service.BuscarConocimientoService;
import cl.casol.backend.conocimiento.application.service.MantenerConocimientoService;
import cl.casol.backend.conocimiento.domain.ResultadoBusquedaConocimiento;
import cl.casol.backend.conocimiento.infrastructure.web.ConocimientoController;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConocimientoController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class Hu04SecurityIntegrationTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean MantenerConocimientoService mantener;
    @MockitoBean BuscarConocimientoService buscador;
    @MockitoBean TokenServicePort tokenService;
    @MockitoBean UsuarioRepository usuarios;

    @Test
    void administradorPuedeBuscarYRecibeDtoLigero() throws Exception {
        autenticar("admin", "ADMINISTRADOR");
        when(buscador.buscar("firewall", 1, 6, null, null)).thenReturn(List.of(resultado()));

        mockMvc.perform(get("/api/conocimientos/buscar")
                        .header("Authorization", "Bearer admin")
                        .param("texto", "firewall").param("hardwareId", "1").param("sistemaId", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].hardwareNombre").value("POS Venta"))
                .andExpect(jsonPath("$[0].relevancia").value(2.5));
    }

    @Test
    void tecnicoPuedeBuscarSoloConFiltros() throws Exception {
        autenticar("tecnico", "TECNICO");
        when(buscador.buscar(null, 1, null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/conocimientos/buscar")
                        .header("Authorization", "Bearer tecnico").param("hardwareId", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        verify(buscador).buscar(null, 1, null, null, null);
    }

    @Test
    void sinCoincidenciasDevuelve200ConColeccionVacia() throws Exception {
        autenticar("tecnico", "TECNICO");
        when(buscador.buscar("inexistente", null, null, null, null)).thenReturn(List.of());
        mockMvc.perform(get("/api/conocimientos/buscar")
                        .header("Authorization", "Bearer tecnico").param("texto", "inexistente"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void sinJwtDevuelve401() throws Exception {
        mockMvc.perform(get("/api/conocimientos/buscar")).andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioPuedeBuscarPublicados() throws Exception {
        autenticar("usuario", "USUARIO");
        when(buscador.buscar(null, null, null, null, null)).thenReturn(List.of(resultado()));
        mockMvc.perform(get("/api/conocimientos/buscar").header("Authorization", "Bearer usuario"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    private void autenticar(String token, String rol) {
        Usuario usuario = new Usuario(7, new Rol(1, rol, null, true, null, null), "Carolina",
                "usuario@dimarsa.cl", "hash", true, null, null);
        when(tokenService.esValido(token)).thenReturn(true);
        when(tokenService.obtenerEmail(token)).thenReturn(usuario.getEmail());
        when(usuarios.buscarPorEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
    }

    private ResultadoBusquedaConocimiento resultado() {
        return new ResultadoBusquedaConocimiento(10, "Firewall", 1, "POS Venta", 6, "Ventas",
                null, null, null, null, 2.5);
    }
}
