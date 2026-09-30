package cl.casol.backend;

import cl.casol.backend.ejecucion.application.service.GestionarEjecucionService;
import cl.casol.backend.ejecucion.domain.*;
import cl.casol.backend.ejecucion.infrastructure.web.EjecucionController;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
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
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EjecucionController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class})
class Hu06SecurityIntegrationTest {
 @Autowired MockMvc mvc; @MockitoBean GestionarEjecucionService service;
 @MockitoBean TokenServicePort tokens; @MockitoBean UsuarioRepository usuarios;
 @Test void todosLosEndpointsSinJwtSon401() throws Exception {mvc.perform(get("/api/ejecuciones")).andExpect(status().isUnauthorized());mvc.perform(get("/api/ejecuciones/1")).andExpect(status().isUnauthorized());mvc.perform(post("/api/procedimientos/1/ejecuciones")).andExpect(status().isUnauthorized());mvc.perform(patch("/api/ejecuciones/1/completar")).andExpect(status().isUnauthorized());}
 @Test void rolNoAutorizadoEs403() throws Exception {auth("x","AUDITOR");mvc.perform(get("/api/ejecuciones").header("Authorization","Bearer x")).andExpect(status().isForbidden());}
 @Test void tecnicoIniciaUsandoIdentidadJwtSinBody() throws Exception {auth("t","TECNICO");when(service.iniciar(5,"u@x.cl")).thenReturn(run());mvc.perform(post("/api/procedimientos/5/ejecuciones").header("Authorization","Bearer t")).andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("EN_CURSO")).andExpect(jsonPath("$.fechaFin").doesNotExist());verify(service).iniciar(5,"u@x.cl");}
 @Test void administradorPuedeListar() throws Exception {auth("a","ADMINISTRADOR");when(service.listar("u@x.cl")).thenReturn(List.of(run()));mvc.perform(get("/api/ejecuciones").header("Authorization","Bearer a")).andExpect(status().isOk()).andExpect(jsonPath("$[0].usuario.id").value(7));}
 @Test void observacionPasoMayorA300Es400YNoInvocaServicio() throws Exception {auth("t","TECNICO");String obs="x".repeat(301);mvc.perform(patch("/api/ejecuciones/1/pasos/2").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content("{\"cumplido\":true,\"observacion\":\""+obs+"\"}" )).andExpect(status().isBadRequest());verify(service,never()).actualizarPaso(anyInt(),anyInt(),anyBoolean(),any(),anyString());}
 @Test void actualizarSoloAceptaCumplidoYObservacion() throws Exception {auth("t","TECNICO");EjecucionPaso ep=new EjecucionPaso(2,1,9,true,"ok",LocalDateTime.now());when(service.actualizarPaso(1,2,true,"ok","u@x.cl")).thenReturn(new DetalleEjecucionPaso(ep,3,"Instruccion",true));mvc.perform(patch("/api/ejecuciones/1/pasos/2").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content("{\"cumplido\":true,\"observacion\":\"ok\",\"pasoId\":999,\"ejecucionId\":999,\"orden\":88}" )).andExpect(status().isOk()).andExpect(jsonPath("$.pasoId").value(9)).andExpect(jsonPath("$.orden").value(3));}
 @Test void cancelarAceptaObservacionOpcional() throws Exception {auth("t","TECNICO");Ejecucion c=new Ejecucion(1,5,user("TECNICO"),LocalDateTime.now(),LocalDateTime.now(),EstadoEjecucion.CANCELADA,"motivo");when(service.cancelar(1,"motivo","u@x.cl")).thenReturn(c);mvc.perform(patch("/api/ejecuciones/1/cancelar").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content("{\"observaciones\":\"motivo\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));}
 void auth(String token,String rol){when(tokens.esValido(token)).thenReturn(true);when(tokens.obtenerEmail(token)).thenReturn("u@x.cl");when(usuarios.buscarPorEmail("u@x.cl")).thenReturn(Optional.of(user(rol)));}
 Usuario user(String rol){return new Usuario(7,new Rol(1,rol,null,true,null,null),"U","u@x.cl","h",true,null,null);}
 Ejecucion run(){return new Ejecucion(1,5,user("TECNICO"),LocalDateTime.now(),null,EstadoEjecucion.EN_CURSO,null);}
}
