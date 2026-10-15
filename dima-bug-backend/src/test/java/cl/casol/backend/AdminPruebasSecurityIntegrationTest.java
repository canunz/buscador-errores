package cl.casol.backend;
import cl.casol.backend.conocimiento.application.service.*;
import cl.casol.backend.conocimiento.domain.Prueba;
import cl.casol.backend.conocimiento.domain.exception.*;
import cl.casol.backend.conocimiento.infrastructure.web.AdminPruebaController;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.domain.exception.*;
import cl.casol.backend.identidad.infrastructure.web.*;
import cl.casol.backend.identidad.infrastructure.security.*;
import cl.casol.backend.shared.infrastructure.security.SecurityConfig;
import cl.casol.backend.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers=AdminPruebaController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class})
class AdminPruebasSecurityIntegrationTest {
 @Autowired MockMvc mvc;
 @MockitoBean AdministrarPruebasService service;
 @MockitoBean TokenServicePort tokens; @MockitoBean UsuarioRepository usuarios;
 @MockitoBean UsuarioAutenticadoCache cache;
 @MockitoBean cl.casol.backend.shared.application.service.VisibilidadContenidoService visibilidad;
 final Prueba p=new Prueba(1,"Ping","Responde",true);
 void auth(String rol){when(tokens.esValido("jwt")).thenReturn(true);when(tokens.obtenerEmail("jwt")).thenReturn("a@x.cl");when(usuarios.buscarPorEmail("a@x.cl")).thenReturn(Optional.of(new Usuario(1,new Rol(1,rol,null,true,null,null),"A","a@x.cl","h",true,null,null)));}
 @Test void adminCrudIncluyeInactivasSinDelete()throws Exception{auth("ADMINISTRADOR");when(service.listar()).thenReturn(List.of(new Prueba(1,"Ping",null,false)));when(service.buscar(1)).thenReturn(p);when(service.crear("Ping","Responde")).thenReturn(p);when(service.editar(1,"Ping",null)).thenReturn(p);when(service.estado(1,false)).thenReturn(new Prueba(1,"Ping","Responde",false));
 mvc.perform(get("/api/admin/pruebas").header("Authorization","Bearer jwt")).andExpect(status().isOk()).andExpect(jsonPath("$[0].activa").value(false));
 mvc.perform(get("/api/admin/pruebas/1").header("Authorization","Bearer jwt")).andExpect(status().isOk());
 mvc.perform(post("/api/admin/pruebas").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"descripcion\":\"Ping\",\"resultadoEsperado\":\"Responde\"}")).andExpect(status().isCreated()).andExpect(header().string("Location","/api/admin/pruebas/1"));
 mvc.perform(put("/api/admin/pruebas/1").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"descripcion\":\"Ping\"}")).andExpect(status().isOk());
 mvc.perform(patch("/api/admin/pruebas/1/estado").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"activa\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.activa").value(false));
 mvc.perform(delete("/api/admin/pruebas/1").header("Authorization","Bearer jwt")).andExpect(status().isMethodNotAllowed());}
 @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"TECNICO","SOPORTE","USUARIO"})
 void otrosRolesNoAdministran(String rol)throws Exception{auth(rol);mvc.perform(get("/api/admin/pruebas").header("Authorization","Bearer jwt")).andExpect(status().isForbidden());mvc.perform(post("/api/admin/pruebas").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());mvc.perform(put("/api/admin/pruebas/1").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());mvc.perform(patch("/api/admin/pruebas/1/estado").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());verifyNoInteractions(service);}
 @Test void sinJwt401()throws Exception{mvc.perform(get("/api/admin/pruebas")).andExpect(status().isUnauthorized());}
 @Test void camposInvalidosYEstadoInyectado400()throws Exception{auth("ADMINISTRADOR");for(String cuerpo:List.of("{}","{\"descripcion\":\" \"}","{\"descripcion\":\"Ping\",\"activa\":false}"))mvc.perform(post("/api/admin/pruebas").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isBadRequest());mvc.perform(patch("/api/admin/pruebas/1/estado").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());verifyNoInteractions(service);}
 @Test void duplicado409()throws Exception{auth("ADMINISTRADOR");when(service.crear("Ping",null)).thenThrow(new PruebaConflictoException("Duplicado"));mvc.perform(post("/api/admin/pruebas").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"descripcion\":\"Ping\"}")).andExpect(status().isConflict());}
 @Test void inexistente404()throws Exception{auth("ADMINISTRADOR");when(service.buscar(99)).thenThrow(new PruebaNoEncontradaException(99));mvc.perform(get("/api/admin/pruebas/99").header("Authorization","Bearer jwt")).andExpect(status().isNotFound());}
}