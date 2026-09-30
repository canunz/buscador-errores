package cl.casol.backend;

import cl.casol.backend.conocimiento.application.service.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import cl.casol.backend.conocimiento.infrastructure.web.*;
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

@WebMvcTest(controllers={SolucionController.class,SolucionAsignacionController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class})
class SolucionSecurityIntegrationTest {
 @Autowired MockMvc mvc; @MockitoBean MantenerSolucionService soluciones;
 @MockitoBean GestionarAsignacionSolucionService asignaciones; @MockitoBean TokenServicePort tokens;
 @MockitoBean UsuarioRepository usuarios;
 @Test void getSoluciones200Ordenadas() throws Exception { auth("t","TECNICO"); when(soluciones.listar(4)).thenReturn(List.of(
   new Solucion(1,4,"A",TipoSolucion.PASOS,1),new Solucion(2,4,"B",TipoSolucion.DERIVACION,2)));
   mvc.perform(get("/api/conocimientos/4/soluciones").header("Authorization","Bearer t")).andExpect(status().isOk())
    .andExpect(jsonPath("$[0].orden").value(1)).andExpect(jsonPath("$[1].tipo").value("DERIVACION"))
    .andExpect(jsonPath("$[0].conocimientoId").doesNotExist()); }
 @Test void getSolucionesVacio() throws Exception { auth("a","ADMINISTRADOR"); when(soluciones.listar(4)).thenReturn(List.of());
   mvc.perform(get("/api/conocimientos/4/soluciones").header("Authorization","Bearer a")).andExpect(status().isOk()).andExpect(content().json("[]")); }
 @Test void getSolucionesPadre404() throws Exception { auth("t","TECNICO"); when(soluciones.listar(404)).thenThrow(new ConocimientoNoEncontradoException(404));
   mvc.perform(get("/api/conocimientos/404/soluciones").header("Authorization","Bearer t")).andExpect(status().isNotFound()); }
 @Test void postPasos201() throws Exception { auth("t","TECNICO"); when(soluciones.crear(4,"Paso",TipoSolucion.PASOS,1)).thenReturn(new Solucion(1,4,"Paso",TipoSolucion.PASOS,1));
   postSol("{\"descripcion\":\"Paso\",\"tipo\":\"PASOS\",\"orden\":1}",201); }
 @Test void postDerivacion201() throws Exception { auth("t","TECNICO"); when(soluciones.crear(4,"Derivar",TipoSolucion.DERIVACION,1)).thenReturn(new Solucion(1,4,"Derivar",TipoSolucion.DERIVACION,1));
   postSol("{\"descripcion\":\"Derivar\",\"tipo\":\"DERIVACION\",\"orden\":1}",201); }
 @Test void postDefaults() throws Exception { auth("t","TECNICO"); when(soluciones.crear(4,"Paso",null,null)).thenReturn(new Solucion(1,4,"Paso",TipoSolucion.PASOS,1));
   mvc.perform(post("/api/conocimientos/4/soluciones").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content("{\"descripcion\":\"Paso\"}"))
    .andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("PASOS")).andExpect(jsonPath("$.orden").value(1)); }
 @Test void postDescripcionVacia400() throws Exception { auth("t","TECNICO"); postSol("{\"descripcion\":\"  \"}",400); }
 @Test void postTipoInvalido400() throws Exception { auth("t","TECNICO"); postSol("{\"descripcion\":\"X\",\"tipo\":\"OTRO\"}",400); }
 @Test void postOrdenInvalido400() throws Exception { auth("t","TECNICO"); postSol("{\"descripcion\":\"X\",\"orden\":0}",400); }
 @Test void putValido200() throws Exception { auth("t","TECNICO"); when(soluciones.modificar(4,8,"Nueva",TipoSolucion.PASOS,2)).thenReturn(new Solucion(8,4,"Nueva",TipoSolucion.PASOS,2));
   mvc.perform(put("/api/conocimientos/4/soluciones/8").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content("{\"descripcion\":\"Nueva\",\"tipo\":\"PASOS\",\"orden\":2}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.orden").value(2)); }
 @Test void putSolucionInexistente404() throws Exception { auth("t","TECNICO"); when(soluciones.modificar(4,8,"X",TipoSolucion.PASOS,1)).thenThrow(new SolucionNoEncontradaException(8)); putSol(4,8,404); }
 @Test void putSolucionAjena404() throws Exception { auth("t","TECNICO"); when(soluciones.modificar(4,9,"X",TipoSolucion.PASOS,1)).thenThrow(new SolucionNoEncontradaException(9)); putSol(4,9,404); }
 @Test void getAsignaciones200() throws Exception { auth("t","TECNICO"); Departamento d=new Departamento(1,"Soporte TI",true); Responsable r=new Responsable(5,1,"Juan",null,null,true);
   when(asignaciones.listar(4,8)).thenReturn(List.of(new SolucionAsignacion(1,8,null,d,true),new SolucionAsignacion(2,8,r,d,false)));
   mvc.perform(get("/api/conocimientos/4/soluciones/8/asignaciones").header("Authorization","Bearer t")).andExpect(status().isOk())
    .andExpect(jsonPath("$[0].principal").value(true)).andExpect(jsonPath("$[0].departamentoNombre").value("Soporte TI"))
    .andExpect(jsonPath("$[1].responsableNombre").value("Juan")); }
 @Test void getAsignacionesVacio() throws Exception { auth("t","TECNICO"); when(asignaciones.listar(4,8)).thenReturn(List.of());
   mvc.perform(get("/api/conocimientos/4/soluciones/8/asignaciones").header("Authorization","Bearer t")).andExpect(status().isOk()).andExpect(content().json("[]")); }
 @Test void getAsignacionSolucion404() throws Exception { auth("t","TECNICO"); when(asignaciones.listar(4,8)).thenThrow(new SolucionNoEncontradaException(8)); getAsig(4,8,404); }
 @Test void getAsignacionSolucionAjena404() throws Exception { auth("t","TECNICO"); when(asignaciones.listar(4,9)).thenThrow(new SolucionNoEncontradaException(9)); getAsig(4,9,404); }
 @Test void postAsignacionDepartamento201() throws Exception { postAsignacionOk(1,null); }
 @Test void postAsignacionResponsable201() throws Exception { postAsignacionOk(null,5); }
 @Test void postAsignacionAmbos201() throws Exception { postAsignacionOk(1,5); }
 @Test void postAsignacionAmbosNulos400() throws Exception { auth("t","TECNICO"); when(asignaciones.crear(4,8,null,null,true)).thenThrow(new AsignacionSolucionInvalidaException("IDs")); postAsig("{\"principal\":true}",400); }
 @Test void postDepartamento404() throws Exception { auth("t","TECNICO"); when(asignaciones.crear(4,8,99,null,true)).thenThrow(new cl.casol.backend.identidad.domain.exception.DepartamentoNoEncontradoException(99)); postAsig("{\"departamentoId\":99,\"principal\":true}",404); }
 @Test void postResponsable404() throws Exception { auth("t","TECNICO"); when(asignaciones.crear(4,8,null,99,true)).thenThrow(new cl.casol.backend.identidad.domain.exception.ResponsableNoEncontradoException(99)); postAsig("{\"responsableId\":99,\"principal\":true}",404); }
 @Test void postIncoherente400() throws Exception { auth("t","TECNICO"); when(asignaciones.crear(4,8,1,5,true)).thenThrow(new AsignacionSolucionInvalidaException("No pertenece")); postAsig("{\"departamentoId\":1,\"responsableId\":5,\"principal\":true}",400); }
 @Test void putAsignacion200() throws Exception { auth("t","TECNICO"); Departamento d=new Departamento(1,"TI",true); when(asignaciones.modificar(4,8,10,1,null,false)).thenReturn(new SolucionAsignacion(10,8,null,d,false)); putAsig(8,10,"{\"departamentoId\":1,\"principal\":false}",200); }
 @Test void putAsignacion404() throws Exception { auth("t","TECNICO"); when(asignaciones.modificar(4,8,10,1,null,true)).thenThrow(new AsignacionSolucionNoEncontradaException(10)); putAsig(8,10,"{\"departamentoId\":1,\"principal\":true}",404); }
 @Test void putAsignacionAjena404() throws Exception { auth("t","TECNICO"); when(asignaciones.modificar(4,8,11,1,null,true)).thenThrow(new AsignacionSolucionNoEncontradaException(11)); putAsig(8,11,"{\"departamentoId\":1,\"principal\":true}",404); }
 @Test void sinJwt401() throws Exception { mvc.perform(get("/api/conocimientos/4/soluciones")).andExpect(status().isUnauthorized()); mvc.perform(get("/api/conocimientos/4/soluciones/8/asignaciones")).andExpect(status().isUnauthorized()); }
 @Test void rolNoAutorizado403() throws Exception { auth("x","AUDITOR"); mvc.perform(get("/api/conocimientos/4/soluciones").header("Authorization","Bearer x")).andExpect(status().isForbidden()); }
 private void postAsignacionOk(Integer d,Integer r) throws Exception { auth("t","TECNICO"); Departamento dep=d==null?null:new Departamento(d,"TI",true); Responsable res=r==null?null:new Responsable(r,1,"Juan",null,null,true);
   when(asignaciones.crear(4,8,d,r,true)).thenReturn(new SolucionAsignacion(10,8,res,dep,true)); postAsig("{\"departamentoId\":"+d+",\"responsableId\":"+r+",\"principal\":true}",201); }
 private void postSol(String body,int s)throws Exception{mvc.perform(post("/api/conocimientos/4/soluciones").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(s));}
 private void putSol(int c,int id,int s)throws Exception{mvc.perform(put("/api/conocimientos/{c}/soluciones/{id}",c,id).header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content("{\"descripcion\":\"X\",\"tipo\":\"PASOS\",\"orden\":1}")).andExpect(status().is(s));}
 private void getAsig(int c,int id,int s)throws Exception{mvc.perform(get("/api/conocimientos/{c}/soluciones/{id}/asignaciones",c,id).header("Authorization","Bearer t")).andExpect(status().is(s));}
 private void postAsig(String b,int s)throws Exception{mvc.perform(post("/api/conocimientos/4/soluciones/8/asignaciones").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content(b)).andExpect(status().is(s));}
 private void putAsig(int sol,int id,String b,int s)throws Exception{mvc.perform(put("/api/conocimientos/4/soluciones/{sol}/asignaciones/{id}",sol,id).header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content(b)).andExpect(status().is(s));}
 private void auth(String token,String rol){String email=rol.toLowerCase()+"@x.cl";when(tokens.esValido(token)).thenReturn(true);when(tokens.obtenerEmail(token)).thenReturn(email);Rol r=new Rol(1,rol,null,true,null,null);when(usuarios.buscarPorEmail(email)).thenReturn(Optional.of(new Usuario(1,r,"U",email,"h",true,null,null)));}
}
