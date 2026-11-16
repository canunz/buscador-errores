package cl.casol.backend;
import cl.casol.backend.ejecucion.application.service.AutomatizarEvidenciaService;
import cl.casol.backend.ejecucion.infrastructure.web.AutomatizacionEjecucionController;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.infrastructure.security.*;
import cl.casol.backend.shared.infrastructure.security.SecurityConfig;
import cl.casol.backend.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
@WebMvcTest(AutomatizacionEjecucionController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class})
class AutomatizacionSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean AutomatizarEvidenciaService service;
    @MockitoBean TokenServicePort tokens;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean UsuarioAutenticadoCache cache;
    @MockitoBean cl.casol.backend.shared.application.service.VisibilidadContenidoService visibilidad;
    String base="/api/ejecuciones/20/solicitudes-evidencia/"+UUID.randomUUID();
    @Test void sinJwtNoExponeEstadoFotosNiAvisos()throws Exception{
        mvc.perform(get("/api/ejecuciones/20/automatizacion")).andExpect(status().isUnauthorized());
        mvc.perform(get(base+"/fotos/1")).andExpect(status().isUnauthorized());mvc.perform(get("/api/ejecuciones/avisos-evidencia")).andExpect(status().isUnauthorized());verifyNoInteractions(service);
    }
    @Test void usuarioFinalNoPuedeOperarLaAutomatizacion()throws Exception{
        mvc.perform(get("/api/ejecuciones/20/automatizacion").with(user("qa").roles("USUARIO"))).andExpect(status().isForbidden());
        mvc.perform(post(base+"/decision").with(user("qa").roles("USUARIO")).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"aprobar\":true}")).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"ADMINISTRADOR","TECNICO","SOPORTE"})
    void rolesOperacionalesConsultanConIdentidadAutenticada(String rol)throws Exception{
        when(service.consultarEstado(20,"qa")).thenReturn(new AutomatizarEvidenciaService.LecturaAutomatizacion(List.of(),List.of(),true,true,List.of()));
        mvc.perform(get("/api/ejecuciones/20/automatizacion").with(user("qa").roles(rol))).andExpect(status().isOk()).andExpect(jsonPath("$.solicitudes").isEmpty());verify(service).consultarEstado(20,"qa");
    }
    @Test void decisionRequiereVersionYDecisionExplicitas()throws Exception{
        for(String json:List.of("{}","{\"aprobar\":true}","{\"version\":1}"))mvc.perform(post(base+"/decision").with(user("qa").roles("TECNICO")).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void noSePuedeSuministrarUnActorDesdeElBody()throws Exception{
        mvc.perform(post(base+"/decision").with(user("qa").roles("TECNICO")).contentType(MediaType.APPLICATION_JSON).content("{\"version\":2,\"aprobar\":true,\"actorId\":999}")).andExpect(status().isOk());
        verify(service).decidir(20,base.substring(base.lastIndexOf('/')+1),2L,true,false,null,"qa");
    }
    @Test void nuevasAccionesConservanJwtYRolOperacional() throws Exception {
        mvc.perform(post(base+"/nueva-solicitud").contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}")).andExpect(status().isUnauthorized());
        mvc.perform(post(base+"/nueva-solicitud").with(user("qa").roles("USUARIO")).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}")).andExpect(status().isForbidden());
        mvc.perform(multipart(base+"/evidencias").with(user("qa").roles("USUARIO")).param("remitente","qa@dimarsa.cl").param("mensajeId",UUID.randomUUID().toString())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void solicitudAjenaResponde403()throws Exception{
        when(service.consultarEstado(20,"qa")).thenThrow(new org.springframework.security.access.AccessDeniedException("Ajena"));
        mvc.perform(get("/api/ejecuciones/20/automatizacion").with(user("qa").roles("TECNICO"))).andExpect(status().isForbidden());
    }
}
