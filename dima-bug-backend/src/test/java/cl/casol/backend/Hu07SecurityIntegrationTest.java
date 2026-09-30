package cl.casol.backend;

import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.infrastructure.security.JwtAuthenticationFilter;
import cl.casol.backend.seguimiento.application.service.RegistrarEfectividadSolucionService;
import cl.casol.backend.seguimiento.domain.*;
import cl.casol.backend.seguimiento.infrastructure.web.ResultadoSolucionController;
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

@WebMvcTest(ResultadoSolucionController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class})
class Hu07SecurityIntegrationTest {
 @Autowired MockMvc mvc; @MockitoBean RegistrarEfectividadSolucionService service;
 @MockitoBean TokenServicePort tokens; @MockitoBean UsuarioRepository usuarios;
 String base="/api/conocimientos/10/soluciones/5";
 @Test void sinJwtEs401EnTodosLosEndpoints() throws Exception {mvc.perform(post(base+"/resultados").contentType(MediaType.APPLICATION_JSON).content("{\"funciono\":true}")).andExpect(status().isUnauthorized());mvc.perform(get(base+"/resultados")).andExpect(status().isUnauthorized());mvc.perform(get(base+"/efectividad")).andExpect(status().isUnauthorized());}
 @Test void rolNoAutorizadoEs403() throws Exception {auth("x","AUDITOR");mvc.perform(get(base+"/efectividad").header("Authorization","Bearer x")).andExpect(status().isForbidden());}
 @Test void tecnicoRegistraTrueConIdentidadJwt() throws Exception {postOk("t","TECNICO",true,null);verify(service).registrar(10,5,true,null,"tecnico@x.cl");}
 @Test void tecnicoRegistraFalse() throws Exception {postOk("t","TECNICO",false,"fallo");verify(service).registrar(10,5,false,"fallo","tecnico@x.cl");}
 @Test void administradorRegistra() throws Exception {postOk("a","ADMINISTRADOR",true,"ok");verify(service).registrar(10,5,true,"ok","administrador@x.cl");}
 @Test void comentarioDe300CaracteresPermitido() throws Exception {String c="x".repeat(300);auth("t","TECNICO");when(service.registrar(10,5,true,c,"tecnico@x.cl")).thenReturn(detalle(true,c));mvc.perform(post(base+"/resultados").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content("{\"funciono\":true,\"comentario\":\""+c+"\"}")).andExpect(status().isCreated());}
 @Test void comentarioMayorA300Es400() throws Exception {auth("t","TECNICO");mvc.perform(post(base+"/resultados").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content("{\"funciono\":true,\"comentario\":\""+"x".repeat(301)+"\"}")).andExpect(status().isBadRequest());verifyNoInteractions(service);}
 @Test void funcionoEsObligatorio() throws Exception {auth("t","TECNICO");mvc.perform(post(base+"/resultados").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());}
 @Test void respuestaNoExponeHashNiIdsAsignables() throws Exception {auth("t","TECNICO");when(service.registrar(10,5,true,null,"tecnico@x.cl")).thenReturn(detalle(true,null));mvc.perform(post(base+"/resultados").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content("{\"funciono\":true,\"usuarioId\":99,\"fecha\":\"2000-01-01T00:00:00\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.usuario.id").value(7)).andExpect(jsonPath("$.usuario.passwordHash").doesNotExist()).andExpect(jsonPath("$.solucionId").doesNotExist());}
 @Test void tecnicoConsultaHistorial() throws Exception {auth("t","TECNICO");when(service.listar(10,5)).thenReturn(List.of(detalle(true,"ok"),detalle(false,"no")));mvc.perform(get(base+"/resultados").header("Authorization","Bearer t")).andExpect(status().isOk()).andExpect(jsonPath("$[0].funciono").value(true)).andExpect(jsonPath("$[1].funciono").value(false));}
 @Test void historialVacioEsListaVacia() throws Exception {auth("a","ADMINISTRADOR");when(service.listar(10,5)).thenReturn(List.of());mvc.perform(get(base+"/resultados").header("Authorization","Bearer a")).andExpect(status().isOk()).andExpect(content().json("[]"));}
 @Test void tecnicoConsultaEfectividad() throws Exception {efectividad("t","TECNICO",new EfectividadSolucion(5,4,3,1,75.0),75.0);}
 @Test void administradorConsultaEfectividad() throws Exception {efectividad("a","ADMINISTRADOR",new EfectividadSolucion(5,3,3,0,100.0),100.0);}
 @Test void sinResultadosDevuelvePorcentajeNull() throws Exception {auth("t","TECNICO");when(service.obtenerEfectividad(10,5)).thenReturn(new EfectividadSolucion(5,0,0,0,null));mvc.perform(get(base+"/efectividad").header("Authorization","Bearer t")).andExpect(status().isOk()).andExpect(jsonPath("$.totalAplicaciones").value(0)).andExpect(jsonPath("$.porcentajeEfectividad").doesNotExist());}
 void postOk(String token,String rol,boolean ok,String comentario) throws Exception {auth(token,rol);String email=rol.toLowerCase()+"@x.cl";when(service.registrar(10,5,ok,comentario,email)).thenReturn(detalle(ok,comentario));String c=comentario==null?"":" ,\"comentario\":\""+comentario+"\"";mvc.perform(post(base+"/resultados").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"funciono\":"+ok+c+"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.funciono").value(ok));}
 void efectividad(String token,String rol,EfectividadSolucion e,double pct) throws Exception {auth(token,rol);when(service.obtenerEfectividad(10,5)).thenReturn(e);mvc.perform(get(base+"/efectividad").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.porcentajeEfectividad").value(pct));}
 void auth(String token,String rol){String email=rol.toLowerCase()+"@x.cl";when(tokens.esValido(token)).thenReturn(true);when(tokens.obtenerEmail(token)).thenReturn(email);when(usuarios.buscarPorEmail(email)).thenReturn(Optional.of(user(rol)));}
 Usuario user(String rol){return new Usuario(7,new Rol(1,rol,null,true,null,null),"U",rol.toLowerCase()+"@x.cl","secreto",true,null,null);}
 DetalleResultadoSolucion detalle(boolean ok,String c){return new DetalleResultadoSolucion(new ResultadoSolucion(1,5,7,ok,c,LocalDateTime.now()),user("TECNICO"));}
}
