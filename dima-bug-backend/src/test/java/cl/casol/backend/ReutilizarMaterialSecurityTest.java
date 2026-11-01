package cl.casol.backend;
import cl.casol.backend.conocimiento.application.service.ReutilizarMaterialService;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import cl.casol.backend.conocimiento.infrastructure.web.ReutilizarMaterialController;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.infrastructure.security.*;
import cl.casol.backend.shared.infrastructure.security.SecurityConfig;
import cl.casol.backend.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
@WebMvcTest(ReutilizarMaterialController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class})
class ReutilizarMaterialSecurityTest {
    @Autowired MockMvc mvc; @MockitoBean ReutilizarMaterialService service;
    @MockitoBean TokenServicePort tokens;@MockitoBean UsuarioRepository usuarios;@MockitoBean UsuarioAutenticadoCache cache;
    @MockitoBean cl.casol.backend.shared.application.service.VisibilidadContenidoService visibilidad;
    final String url="/api/conocimientos/2/materiales/reutilizaciones";
    final String body="{\"conocimientoOrigenId\":1,\"materialOrigenId\":3}";
    void auth(String rol){when(tokens.esValido("jwt")).thenReturn(true);when(tokens.obtenerEmail("jwt")).thenReturn("a@example.invalid");when(usuarios.buscarPorEmail("a@example.invalid")).thenReturn(Optional.of(new Usuario(1,new Rol(1,rol,null,true,null,null),"A","a@example.invalid","h",true,null,null)));}
    @ParameterizedTest @ValueSource(strings={"ADMINISTRADOR","TECNICO","SOPORTE"}) void staffCopiaConRutaDelNuevoDestino(String rol)throws Exception{auth(rol);when(service.reutilizar(1,3,2)).thenReturn(new MaterialApoyo(99,2,null,"Manual",TipoMaterial.PDF,"file:materiales/9d50c602-4bf2-438a-82c3-e197c9153250"));mvc.perform(post(url).header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(99)).andExpect(jsonPath("$.url").value("/api/conocimientos/2/materiales/99/archivo"));}
    @Test void sinJwt401()throws Exception{mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
    @ParameterizedTest @ValueSource(strings={"USUARIO","AUDITOR"}) void rolesAjeno403(String rol)throws Exception{auth(rol);mvc.perform(post(url).header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());verifyNoInteractions(service);}
    @Test void camposClienteNoPuedenInyectarArchivo()throws Exception{auth("ADMINISTRADOR");for(String json:List.of("{}","{\"conocimientoOrigenId\":0,\"materialOrigenId\":3}","{\"conocimientoOrigenId\":1,\"materialOrigenId\":3,\"url\":\"file:/tmp/x\"}"))mvc.perform(post(url).header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest());verifyNoInteractions(service);}
    @Test void duplicado409YOrigenNoPublicado400()throws Exception{auth("TECNICO");doThrow(new MaterialDuplicadoException()).when(service).reutilizar(1,3,2);mvc.perform(post(url).header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());doThrow(new ClasificacionInvalidaException("Origen PUBLICADO obligatorio")).when(service).reutilizar(1,3,2);mvc.perform(post(url).header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());}
}
