package cl.casol.backend;

import cl.casol.backend.conocimiento.application.service.MantenerMaterialApoyoService;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import cl.casol.backend.conocimiento.infrastructure.web.MaterialApoyoController;
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
import java.util.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=MaterialApoyoController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class})
class MaterialApoyoSecurityIntegrationTest {
 @Autowired MockMvc mvc; @MockitoBean MantenerMaterialApoyoService service;
 @MockitoBean TokenServicePort tokens; @MockitoBean UsuarioRepository usuarios;
 @Test void getConMateriales200Dto() throws Exception { auth("t","TECNICO"); when(service.listar(4)).thenReturn(List.of(
  new MaterialApoyo(1,4,null,"Manual",TipoMaterial.PDF,"https://ejemplo.cl/manual.pdf")));
  mvc.perform(get("/api/conocimientos/4/materiales").header("Authorization","Bearer t")).andExpect(status().isOk())
   .andExpect(jsonPath("$[0].nombre").value("Manual")).andExpect(jsonPath("$[0].tipo").value("PDF"))
   .andExpect(jsonPath("$[0].conocimientoId").doesNotExist()).andExpect(jsonPath("$[0].pasoId").doesNotExist()); }
 @Test void getSinMateriales200VacioAdministrador() throws Exception { auth("a","ADMINISTRADOR"); when(service.listar(4)).thenReturn(List.of());
  mvc.perform(get("/api/conocimientos/4/materiales").header("Authorization","Bearer a")).andExpect(status().isOk()).andExpect(content().json("[]")); }
 @Test void getConocimiento404() throws Exception { auth("t","TECNICO"); when(service.listar(404)).thenThrow(new ConocimientoNoEncontradoException(404));
  mvc.perform(get("/api/conocimientos/404/materiales").header("Authorization","Bearer t")).andExpect(status().isNotFound()); }
 @Test void postImagen201(){ postTipo(TipoMaterial.IMAGEN); }
 @Test void postPdf201(){ postTipo(TipoMaterial.PDF); }
 @Test void postVideo201(){ postTipo(TipoMaterial.VIDEO); }
 @Test void postEnlace201(){ postTipo(TipoMaterial.ENLACE); }
 @Test void postConocimiento404() throws Exception { auth("t","TECNICO"); when(service.crear(404,"M",TipoMaterial.PDF,"url")).thenThrow(new ConocimientoNoEncontradoException(404));
  mvc.perform(post("/api/conocimientos/404/materiales").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content(json("M","PDF","url"))).andExpect(status().isNotFound()); }
 @Test void nombreVacio400() throws Exception { validar(json("  ","PDF","url")); }
 @Test void nombreLargo400() throws Exception { validar(json("a".repeat(151),"PDF","url")); }
 @Test void tipoInvalido400() throws Exception { validar(json("M","ARCHIVO","url")); }
 @Test void urlVacia400() throws Exception { validar(json("M","PDF","  ")); }
 @Test void urlLarga400() throws Exception { validar(json("M","PDF","a".repeat(501))); }
 @Test void putValido200() throws Exception { auth("t","TECNICO"); when(service.modificar(4,2,"Nuevo",TipoMaterial.PDF,"v2")).thenReturn(
  new MaterialApoyo(2,4,null,"Nuevo",TipoMaterial.PDF,"v2"));
  mvc.perform(put("/api/conocimientos/4/materiales/2").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content(json("Nuevo","PDF","v2")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Nuevo")); }
 @Test void putMaterialInexistente404() throws Exception { put404(2); }
 @Test void putMaterialOtroConocimiento404() throws Exception { put404(3); }
 @Test void putMaterialDePaso404() throws Exception { put404(4); }
 @Test void putRequestInvalido400() throws Exception { auth("t","TECNICO"); mvc.perform(put("/api/conocimientos/4/materiales/2").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content(json("","PDF","url"))).andExpect(status().isBadRequest()); }
 @Test void sinJwt401() throws Exception { mvc.perform(get("/api/conocimientos/4/materiales")).andExpect(status().isUnauthorized()); }
 @Test void rolNoAutorizado403() throws Exception { auth("x","AUDITOR"); mvc.perform(get("/api/conocimientos/4/materiales").header("Authorization","Bearer x")).andExpect(status().isForbidden()); }
 private void postTipo(TipoMaterial tipo){ try { auth("t","TECNICO"); when(service.crear(4,"M",tipo,"url")).thenReturn(new MaterialApoyo(1,4,null,"M",tipo,"url"));
  mvc.perform(post("/api/conocimientos/4/materiales").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content(json("M",tipo.name(),"url"))).andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value(tipo.name())); } catch(Exception e){throw new RuntimeException(e);} }
 private void validar(String body)throws Exception{auth("t","TECNICO");mvc.perform(post("/api/conocimientos/4/materiales").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());}
 private void put404(int id)throws Exception{auth("t","TECNICO");when(service.modificar(4,id,"N",TipoMaterial.PDF,"url")).thenThrow(new MaterialApoyoNoEncontradoException(id));mvc.perform(put("/api/conocimientos/4/materiales/{id}",id).header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content(json("N","PDF","url"))).andExpect(status().isNotFound());}
 private String json(String n,String t,String u){return "{\"nombre\":\""+n+"\",\"tipo\":\""+t+"\",\"url\":\""+u+"\"}";}
 private void auth(String token,String rol){String email=rol.toLowerCase()+"@x.cl";when(tokens.esValido(token)).thenReturn(true);when(tokens.obtenerEmail(token)).thenReturn(email);Rol r=new Rol(1,rol,null,true,null,null);when(usuarios.buscarPorEmail(email)).thenReturn(Optional.of(new Usuario(1,r,"U",email,"h",true,null,null)));}
}
