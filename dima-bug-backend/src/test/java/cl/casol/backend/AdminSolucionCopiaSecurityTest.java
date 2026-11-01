package cl.casol.backend;
import cl.casol.backend.conocimiento.application.service.CopiarSolucionService;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import cl.casol.backend.conocimiento.infrastructure.web.AdminSolucionCopiaController;
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
@WebMvcTest(AdminSolucionCopiaController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class})
class AdminSolucionCopiaSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean CopiarSolucionService service;
    @MockitoBean TokenServicePort tokens; @MockitoBean UsuarioRepository usuarios;
    @MockitoBean UsuarioAutenticadoCache cache;
    @MockitoBean cl.casol.backend.shared.application.service.VisibilidadContenidoService visibilidad;
    final String url="/api/admin/conocimientos/2/soluciones/copias";
    void auth(String rol){when(tokens.esValido("jwt")).thenReturn(true);when(tokens.obtenerEmail("jwt")).thenReturn("a@example.invalid");when(usuarios.buscarPorEmail("a@example.invalid")).thenReturn(Optional.of(new Usuario(1,new Rol(1,rol,null,true,null,null),"A","a@example.invalid","h",true,null,null)));}
    @Test void sinJwt401()throws Exception{mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
    @ParameterizedTest @ValueSource(strings={"TECNICO","SOPORTE","USUARIO"}) void otrosRoles403(String rol)throws Exception{auth(rol);mvc.perform(post(url).header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());verifyNoInteractions(service);}
    @Test void adminCreaPrivadaPorDefecto()throws Exception{
        auth("ADMINISTRADOR");when(service.copiar(1,8,2,false)).thenReturn(new Solucion(99,2,"D",TipoSolucion.PASOS,3,"T",true,false));
        mvc.perform(post(url).header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"conocimientoOrigenId\":1,\"solucionOrigenId\":8}"))
          .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(99)).andExpect(jsonPath("$.autoservicio").value(false));verify(service).copiar(1,8,2,false);
    }
    @Test void adminConfirmaAutoservicio()throws Exception{
        auth("ADMINISTRADOR");when(service.copiar(1,8,2,true)).thenReturn(new Solucion(99,2,"D",TipoSolucion.PASOS,3,"T",true,true));
        mvc.perform(post(url).header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"conocimientoOrigenId\":1,\"solucionOrigenId\":8,\"confirmarAutoservicio\":true}")).andExpect(status().isCreated()).andExpect(jsonPath("$.autoservicio").value(true));
    }
    @Test void validaIdsYNoAceptaContenidoCliente()throws Exception{
        auth("ADMINISTRADOR");for(String json:List.of("{}","{\"conocimientoOrigenId\":0,\"solucionOrigenId\":8}","{\"conocimientoOrigenId\":1,\"solucionOrigenId\":8,\"titulo\":\"inyectado\"}"))
          mvc.perform(post(url).header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void duplicado409()throws Exception{auth("ADMINISTRADOR");when(service.copiar(1,8,2,false)).thenThrow(new SolucionDuplicadaException());mvc.perform(post(url).header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"conocimientoOrigenId\":1,\"solucionOrigenId\":8}")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").exists());}
    @Test void borrador400YDestino404()throws Exception{auth("ADMINISTRADOR");when(service.copiar(1,8,2,false)).thenThrow(new CopiaSolucionInvalidaException("Origen debe estar PUBLICADO"));mvc.perform(post(url).header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"conocimientoOrigenId\":1,\"solucionOrigenId\":8}")).andExpect(status().isBadRequest());doThrow(new ConocimientoNoEncontradoException(2)).when(service).copiar(1,8,2,false);mvc.perform(post(url).header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"conocimientoOrigenId\":1,\"solucionOrigenId\":8}")).andExpect(status().isNotFound());}
}
