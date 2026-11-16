package cl.casol.backend;

import cl.casol.backend.identidad.application.port.out.TokenServicePort;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.Rol;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.infrastructure.security.JwtAuthenticationFilter;
import cl.casol.backend.identidad.infrastructure.security.UsuarioAutenticadoCache;
import cl.casol.backend.shared.application.service.VisibilidadContenidoService;
import cl.casol.backend.shared.infrastructure.security.SecurityConfig;
import cl.casol.backend.shared.infrastructure.web.GlobalExceptionHandler;
import cl.casol.backend.tickets.application.port.in.*;
import cl.casol.backend.tickets.application.port.out.ClasificacionTicket;
import cl.casol.backend.tickets.domain.*;
import cl.casol.backend.tickets.domain.exception.AccesoTicketDenegadoException;
import cl.casol.backend.tickets.domain.exception.TicketNoEncontradoException;
import cl.casol.backend.tickets.domain.exception.TransicionEstadoInvalidaException;
import cl.casol.backend.tickets.infrastructure.web.EtiquetaController;
import cl.casol.backend.tickets.infrastructure.web.TicketController;
import cl.casol.backend.tickets.infrastructure.web.TicketExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({TicketController.class, EtiquetaController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class, TicketExceptionHandler.class})
class TicketSecurityIntegrationTest {
    @Autowired MockMvc mvc;

    @MockitoBean CrearTicketUseCase crear;
    @MockitoBean ConsultarTicketsUseCase consultar;
    @MockitoBean AsignarTicketUseCase asignar;
    @MockitoBean CambiarEstadoTicketUseCase cambiarEstado;
    @MockitoBean ResolverTicketUseCase resolver;
    @MockitoBean ComentarTicketUseCase comentar;
    @MockitoBean EtiquetarTicketUseCase etiquetar;
    @MockitoBean GestionarEtiquetasUseCase etiquetas;

    @MockitoBean TokenServicePort tokens;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean UsuarioAutenticadoCache usuarioAutenticadoCache;
    @MockitoBean VisibilidadContenidoService visibilidadContenidoService;

    @Test void sinJwtEs401() throws Exception {
        mvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/etiquetas")).andExpect(status().isUnauthorized());
        verifyNoInteractions(consultar, etiquetas);
    }

    @Test void rolDesconocidoEs403() throws Exception {
        auth("AUDITOR");
        mvc.perform(get("/api/tickets").header("Authorization", "Bearer x")).andExpect(status().isForbidden());
    }

    @Test void usuarioListaConSuIdentidadDelJwt() throws Exception {
        auth("USUARIO");
        when(consultar.listar(any(), eq(0), eq(10), eq("usuario@x.cl")))
                .thenReturn(new PaginaTickets<>(List.of(vista()), 0, 10, 1));
        mvc.perform(get("/api/tickets").param("estado", "ABIERTO").header("Authorization", "Bearer x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].codigo").value("TK-2026-0001"))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test void estadoInvalidoEnFiltroEs400() throws Exception {
        auth("SOPORTE");
        mvc.perform(get("/api/tickets").param("estado", "INVENTADO").header("Authorization", "Bearer x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test void crearSinTituloEs400() throws Exception {
        auth("USUARIO");
        mvc.perform(post("/api/tickets").header("Authorization", "Bearer x").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"\",\"descripcion\":\"d\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(crear);
    }

    @Test void usuarioCreaTicket() throws Exception {
        auth("USUARIO");
        when(crear.crear(any(), eq("usuario@x.cl"))).thenReturn(vista());
        mvc.perform(post("/api/tickets").header("Authorization", "Bearer x").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Impresora\",\"descripcion\":\"No imprime\",\"prioridad\":\"ALTA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ABIERTO"));
    }

    @Test void sinPermisoEnCasoDeUsoEs403() throws Exception {
        auth("USUARIO");
        when(asignar.asignar(eq(1L), eq(3), anyString()))
                .thenThrow(new AccesoTicketDenegadoException("Solo soporte puede asignar tickets"));
        mvc.perform(patch("/api/tickets/1/asignacion").header("Authorization", "Bearer x")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"asignadoId\":3}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Solo soporte puede asignar tickets"));
    }

    @Test void transicionInvalidaEs409() throws Exception {
        auth("TECNICO");
        when(cambiarEstado.cambiarEstado(eq(1L), eq(EstadoTicket.EN_ESPERA), any(), eq("tecnico@x.cl")))
                .thenThrow(new TransicionEstadoInvalidaException(EstadoTicket.CERRADO, EstadoTicket.EN_ESPERA));
        mvc.perform(patch("/api/tickets/1/estado").header("Authorization", "Bearer x")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"EN_ESPERA\"}"))
                .andExpect(status().isConflict());
    }

    @Test void ticketInexistenteEs404() throws Exception {
        auth("SOPORTE");
        when(consultar.detalle(eq(99L), anyString())).thenThrow(new TicketNoEncontradoException(99L));
        mvc.perform(get("/api/tickets/99").header("Authorization", "Bearer x")).andExpect(status().isNotFound());
    }

    @Test void todosLosRolesListanEtiquetasActivas() throws Exception {
        auth("USUARIO");
        when(etiquetas.listar(false, "usuario@x.cl")).thenReturn(List.of(new Etiqueta(1L, "red", "#1E4FD6", true)));
        mvc.perform(get("/api/etiquetas").header("Authorization", "Bearer x"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nombre").value("red"));
    }

    @Test void soloSoporteCreaEditaYDesactivaEtiquetas() throws Exception {
        for (String rol : List.of("USUARIO", "ADMINISTRADOR")) {
            auth(rol);
            mvc.perform(post("/api/etiquetas").header("Authorization", "Bearer x").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nombre\":\"red\",\"color\":\"#1E4FD6\"}")).andExpect(status().isForbidden());
            mvc.perform(patch("/api/etiquetas/1/estado").header("Authorization", "Bearer x")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"activa\":false}")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(etiquetas);

        auth("SOPORTE");
        when(etiquetas.crear("red", "#1E4FD6", null, "soporte@x.cl")).thenReturn(new Etiqueta(1L, "red", "#1E4FD6", true));
        mvc.perform(post("/api/etiquetas").header("Authorization", "Bearer x").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"red\",\"color\":\"#1E4FD6\"}")).andExpect(status().isCreated());
    }

    @Test void colorInvalidoEs400() throws Exception {
        auth("SOPORTE");
        mvc.perform(post("/api/etiquetas").header("Authorization", "Bearer x").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"red\",\"color\":\"azul\"}")).andExpect(status().isBadRequest());
    }

    private void auth(String rol) {
        String email = rol.toLowerCase() + "@x.cl";
        when(tokens.esValido("x")).thenReturn(true);
        when(tokens.obtenerEmail("x")).thenReturn(email);
        when(usuarioAutenticadoCache.obtener(email)).thenReturn(Optional.empty());
        when(usuarios.buscarPorEmail(email)).thenReturn(Optional.of(
                new Usuario(7, new Rol(1, rol, null, true, null, null), "U", email, "secreto", true, null, null)));
    }

    private TicketVista vista() {
        Ticket ticket = new Ticket(1L, "TK-2026-0001", "Impresora", "No imprime", EstadoTicket.ABIERTO,
                PrioridadTicket.ALTA, 7, null, null, null, null, null, null, LocalDateTime.now(), LocalDateTime.now(),
                null, null, Set.of(), List.of(), List.of());
        return new TicketVista(ticket, List.of(), Map.of(), ClasificacionTicket.VACIA, null, List.of());
    }
}
