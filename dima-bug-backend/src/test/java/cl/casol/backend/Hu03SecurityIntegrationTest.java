package cl.casol.backend;

import cl.casol.backend.conocimiento.application.service.MantenerConocimientoService;
import cl.casol.backend.conocimiento.application.service.BuscarConocimientoService;
import cl.casol.backend.conocimiento.domain.Conocimiento;
import cl.casol.backend.conocimiento.domain.EstadoConocimiento;
import cl.casol.backend.conocimiento.domain.exception.ClasificacionInvalidaException;
import cl.casol.backend.identidad.application.port.out.TokenServicePort;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.Rol;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.infrastructure.security.JwtAuthenticationFilter;
import cl.casol.backend.conocimiento.infrastructure.web.ConocimientoController;
import cl.casol.backend.shared.infrastructure.security.SecurityConfig;
import cl.casol.backend.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConocimientoController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class Hu03SecurityIntegrationTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean MantenerConocimientoService service;
    @MockitoBean BuscarConocimientoService buscador;
    @MockitoBean TokenServicePort tokenService;
    @MockitoBean UsuarioRepository usuarioRepository;

    @Test
    void sinJwtRecibe401() throws Exception {
        mockMvc.perform(get("/api/conocimientos")).andExpect(status().isUnauthorized());
    }

    @Test
    void tecnicoPuedeListarSinExponerEntidades() throws Exception {
        autenticar("token-tecnico", "TECNICO");
        when(service.listar()).thenReturn(List.of(conocimiento()));
        mockMvc.perform(get("/api/conocimientos").header("Authorization", "Bearer token-tecnico"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].estado").value("BORRADOR"))
                .andExpect(jsonPath("$[0].creadoPor.nombre").value("Carolina"));
    }

    @Test
    void administradorPuedeConsultar() throws Exception {
        autenticar("token-admin", "ADMINISTRADOR");
        when(service.buscar(1)).thenReturn(conocimiento());
        mockMvc.perform(get("/api/conocimientos/1").header("Authorization", "Bearer token-admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void crearUsaEmailDelJwtYResponde201() throws Exception {
        autenticar("token-tecnico", "TECNICO");
        when(service.crear(eq("Título"), eq("Descripción"), isNull(), isNull(), isNull(), isNull(),
                isNull(), eq("usuario@dimarsa.cl"))).thenReturn(conocimiento());
        mockMvc.perform(post("/api/conocimientos").header("Authorization", "Bearer token-tecnico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Título\",\"descripcion\":\"Descripción\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("BORRADOR"));
    }

    @Test
    void clasificacionInvalidaResponde400() throws Exception {
        autenticar("token-tecnico", "TECNICO");
        when(service.crear(anyString(), anyString(), anyInt(), isNull(), isNull(), isNull(), isNull(), anyString()))
                .thenThrow(new ClasificacionInvalidaException("Hardware inexistente o inactivo"));
        mockMvc.perform(post("/api/conocimientos").header("Authorization", "Bearer token-tecnico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Título\",\"descripcion\":\"Descripción\",\"hardwareId\":99}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tecnicoPuedeModificarPeroNoPublicar() throws Exception {
        autenticar("token-tecnico", "TECNICO");
        when(service.modificar(eq(1), anyString(), anyString(), isNull(), isNull(), isNull(), isNull(),
                isNull(), anyString())).thenReturn(conocimiento());
        String body = "{\"titulo\":\"Título\",\"descripcion\":\"Descripción\"}";
        mockMvc.perform(put("/api/conocimientos/1").header("Authorization", "Bearer token-tecnico")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mockMvc.perform(patch("/api/conocimientos/1/estado").header("Authorization", "Bearer token-tecnico")
                .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PUBLICADO\"}"))
                .andExpect(status().isForbidden());
        verify(service, never()).cambiarEstado(anyInt(), any(), anyString());
    }

    @Test
    void administradorPuedePublicar() throws Exception {
        autenticar("token-admin", "ADMINISTRADOR");
        when(service.cambiarEstado(1, EstadoConocimiento.PUBLICADO, "usuario@dimarsa.cl"))
                .thenReturn(conocimientoPublicado());
        mockMvc.perform(patch("/api/conocimientos/1/estado").header("Authorization", "Bearer token-admin")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PUBLICADO\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PUBLICADO"));
    }

    @Test
    void patchNoPermiteAsignarEliminado() throws Exception {
        autenticar("token-admin", "ADMINISTRADOR");
        mockMvc.perform(patch("/api/conocimientos/1/estado").header("Authorization", "Bearer token-admin")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ELIMINADO\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).cambiarEstado(anyInt(), any(), anyString());
    }

    @Test
    void patchEstadoSinJwtRecibe401() throws Exception {
        mockMvc.perform(patch("/api/conocimientos/1/estado")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PUBLICADO\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administradorPuedeEliminarLogicamente() throws Exception {
        autenticar("token-admin", "ADMINISTRADOR");
        mockMvc.perform(delete("/api/conocimientos/1")
                        .header("Authorization", "Bearer token-admin"))
                .andExpect(status().isNoContent());
        verify(service).eliminar(1, "usuario@dimarsa.cl");
    }

    @Test
    void tecnicoNoPuedeEliminar() throws Exception {
        autenticar("token-tecnico", "TECNICO");
        mockMvc.perform(delete("/api/conocimientos/1")
                        .header("Authorization", "Bearer token-tecnico"))
                .andExpect(status().isForbidden());
        verify(service, never()).eliminar(anyInt(), anyString());
    }

    @Test
    void eliminarSinJwtRecibe401() throws Exception {
        mockMvc.perform(delete("/api/conocimientos/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void eliminarInexistenteRecibe404() throws Exception {
        autenticar("token-admin", "ADMINISTRADOR");
        doThrow(new cl.casol.backend.conocimiento.domain.exception.ConocimientoNoEncontradoException(404))
                .when(service).eliminar(404, "usuario@dimarsa.cl");
        mockMvc.perform(delete("/api/conocimientos/404")
                        .header("Authorization", "Bearer token-admin"))
                .andExpect(status().isNotFound());
    }

    @Test
    void tecnicoNoPuedePublicarIndirectamenteConPostOPut() throws Exception {
        autenticar("token-tecnico", "TECNICO");
        when(service.crear(eq("Título"), eq("Descripción"), isNull(), isNull(), isNull(), isNull(),
                isNull(), eq("usuario@dimarsa.cl"))).thenReturn(conocimiento());
        when(service.modificar(eq(1), eq("Título"), eq("Descripción"), isNull(), isNull(), isNull(), isNull(),
                isNull(), eq("usuario@dimarsa.cl"))).thenReturn(conocimiento());
        String body = "{\"titulo\":\"Título\",\"descripcion\":\"Descripción\",\"estado\":\"PUBLICADO\"}";

        mockMvc.perform(post("/api/conocimientos").header("Authorization", "Bearer token-tecnico")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("BORRADOR"));
        mockMvc.perform(put("/api/conocimientos/1").header("Authorization", "Bearer token-tecnico")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("BORRADOR"));
    }

    private void autenticar(String token, String rol) {
        Usuario usuario = usuario(rol);
        when(tokenService.esValido(token)).thenReturn(true);
        when(tokenService.obtenerEmail(token)).thenReturn(usuario.getEmail());
        when(usuarioRepository.buscarPorEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
    }

    private Usuario usuario(String rol) {
        return new Usuario(7, new Rol(rol.equals("ADMINISTRADOR") ? 1 : 2, rol, null, true, null, null),
                "Carolina", "usuario@dimarsa.cl", "hash", true, null, null);
    }

    private Conocimiento conocimiento() {
        return new Conocimiento(1, "Título", "Descripción", EstadoConocimiento.BORRADOR, null, null, null,
                null, null, usuario("TECNICO"), LocalDateTime.now(), null, null);
    }

    private Conocimiento conocimientoPublicado() {
        Conocimiento c = conocimiento();
        return new Conocimiento(c.getId(), c.getTitulo(), c.getDescripcion(), EstadoConocimiento.PUBLICADO,
                null, null, null, null, null, c.getCreadoPor(), c.getFechaCreacion(), c.getCreadoPor(),
                LocalDateTime.now());
    }
}
