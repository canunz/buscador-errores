package cl.casol.backend;

import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.infrastructure.security.JwtAuthenticationFilter;
import cl.casol.backend.procedimiento.application.service.*;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.procedimiento.domain.exception.*;
import cl.casol.backend.procedimiento.infrastructure.web.*;
import cl.casol.backend.shared.infrastructure.security.SecurityConfig;
import cl.casol.backend.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime; import java.util.*;
import static org.mockito.ArgumentMatchers.*; import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ProcedimientoController.class,PasoController.class,MaterialPasoController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class})
class Hu05SecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean MantenerProcedimientoService procedimientos;
    @MockitoBean MantenerPasoService pasos;
    @MockitoBean MantenerMaterialPasoService materiales;
    @MockitoBean TokenServicePort tokens;
    @MockitoBean UsuarioRepository usuarios;

    @Test void sinJwtRecibe401EnLosTresRecursos() throws Exception {mvc.perform(get("/api/procedimientos")).andExpect(status().isUnauthorized());mvc.perform(get("/api/procedimientos/1/pasos")).andExpect(status().isUnauthorized());mvc.perform(get("/api/procedimientos/1/pasos/2/materiales")).andExpect(status().isUnauthorized());}
    @Test void administradorCreaProcedimiento201Borrador() throws Exception {autenticar("a","ADMINISTRADOR");when(procedimientos.crear("N","D","u@x.cl")).thenReturn(procedimiento(EstadoProcedimiento.BORRADOR));mvc.perform(post("/api/procedimientos").header("Authorization","Bearer a").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"N\",\"descripcion\":\"D\",\"estado\":\"PUBLICADO\",\"creadoPor\":99}" )).andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("BORRADOR"));verify(procedimientos).crear("N","D","u@x.cl");}
    @Test void tecnicoCreaYModificaProcedimiento() throws Exception {autenticar("t","TECNICO");when(procedimientos.crear(anyString(),isNull(),anyString())).thenReturn(procedimiento(EstadoProcedimiento.BORRADOR));when(procedimientos.modificar(eq(1),anyString(),isNull(),anyString())).thenReturn(procedimiento(EstadoProcedimiento.BORRADOR));String b="{\"nombre\":\"N\"}";mvc.perform(post("/api/procedimientos").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content(b)).andExpect(status().isCreated());mvc.perform(put("/api/procedimientos/1").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content(b)).andExpect(status().isOk());}
    @Test void administradorPublica() throws Exception {autenticar("a","ADMINISTRADOR");when(procedimientos.publicar(1,"u@x.cl")).thenReturn(procedimiento(EstadoProcedimiento.PUBLICADO));mvc.perform(patch("/api/procedimientos/1/estado").header("Authorization","Bearer a").contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PUBLICADO\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PUBLICADO"));}
    @Test void tecnicoNoPublica() throws Exception {autenticar("t","TECNICO");mvc.perform(patch("/api/procedimientos/1/estado").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PUBLICADO\"}")).andExpect(status().isForbidden());verify(procedimientos,never()).publicar(anyInt(),anyString());}
    @Test void getInexistente404() throws Exception {autenticar("t","TECNICO");when(procedimientos.buscar(404)).thenThrow(new ProcedimientoNoEncontradoException(404));mvc.perform(get("/api/procedimientos/404").header("Authorization","Bearer t")).andExpect(status().isNotFound());}
    @Test void validaPasoYMapeaConflicto409() throws Exception {autenticar("t","TECNICO");mvc.perform(post("/api/procedimientos/1/pasos").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content("{\"orden\":0,\"instruccion\":\"\",\"esCritico\":false}")).andExpect(status().isBadRequest());when(pasos.crear(1,2,"X",false)).thenThrow(new OrdenPasoDuplicadoException(1,2));mvc.perform(post("/api/procedimientos/1/pasos").header("Authorization","Bearer t").contentType(MediaType.APPLICATION_JSON).content("{\"orden\":2,\"instruccion\":\"X\",\"esCritico\":false}")).andExpect(status().isConflict());}
    @Test void creaPasoCriticoYListaOrdenado() throws Exception {autenticar("t","TECNICO");when(pasos.crear(1,1,"X",true)).thenReturn(new Paso(5,1,1,"X",true));when(pasos.listar(1)).thenReturn(List.of(new Paso(5,1,1,"X",true),new Paso(6,1,2,"Y",false)));String h="Bearer t";mvc.perform(post("/api/procedimientos/1/pasos").header("Authorization",h).contentType(MediaType.APPLICATION_JSON).content("{\"orden\":1,\"instruccion\":\"X\",\"esCritico\":true}")).andExpect(status().isCreated()).andExpect(jsonPath("$.esCritico").value(true));mvc.perform(get("/api/procedimientos/1/pasos").header("Authorization",h)).andExpect(status().isOk()).andExpect(jsonPath("$[0].orden").value(1)).andExpect(jsonPath("$[1].orden").value(2));}
    @Test void creaYModificaMaterialPasoConDtoReutilizado() throws Exception {autenticar("t","TECNICO");MaterialApoyo m=new MaterialApoyo(8,null,5,"Guía",TipoMaterial.PDF,"https://x");when(materiales.crear(1,5,"Guía",TipoMaterial.PDF,"https://x")).thenReturn(m);when(materiales.modificar(1,5,8,"Guía",TipoMaterial.PDF,"https://x")).thenReturn(m);String b="{\"nombre\":\"Guía\",\"tipo\":\"PDF\",\"url\":\"https://x\"}";String h="Bearer t";mvc.perform(post("/api/procedimientos/1/pasos/5/materiales").header("Authorization",h).contentType(MediaType.APPLICATION_JSON).content(b)).andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("PDF"));mvc.perform(put("/api/procedimientos/1/pasos/5/materiales/8").header("Authorization",h).contentType(MediaType.APPLICATION_JSON).content(b)).andExpect(status().isOk());}
    @Test void rolNoAutorizadoRecibe403() throws Exception {autenticar("x","AUDITOR");mvc.perform(get("/api/procedimientos").header("Authorization","Bearer x")).andExpect(status().isForbidden());}
    private void autenticar(String token,String rol){Usuario u=usuario(rol);when(tokens.esValido(token)).thenReturn(true);when(tokens.obtenerEmail(token)).thenReturn("u@x.cl");when(usuarios.buscarPorEmail("u@x.cl")).thenReturn(Optional.of(u));}
    private Usuario usuario(String rol){return new Usuario(7,new Rol(1,rol,null,true,null,null),"Carolina","u@x.cl","h",true,null,null);}
    private Procedimiento procedimiento(EstadoProcedimiento e){Usuario u=usuario("TECNICO");return new Procedimiento(1,"N","D",e,u,LocalDateTime.now(),e==EstadoProcedimiento.PUBLICADO?u:null,e==EstadoProcedimiento.PUBLICADO?LocalDateTime.now():null);}
}
