package cl.casol.backend;
import cl.casol.backend.identidad.application.service.*;
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
@WebMvcTest(controllers={AdminResponsableController.class,AdminDepartamentoController.class,AdminDepartamentoContactoController.class,DepartamentoController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class})
class AdminOrganizacionSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdministrarOrganizacionService service;
    @MockitoBean AdministrarResponsablesService responsables;
    @MockitoBean ConsultarOrganizacionService consultas;
    @MockitoBean TokenServicePort tokens; @MockitoBean UsuarioRepository usuarios;
    @MockitoBean UsuarioAutenticadoCache cache;
    @MockitoBean cl.casol.backend.shared.application.service.VisibilidadContenidoService visibilidad;
    final DepartamentoAdministrativo d=new DepartamentoAdministrativo(1,"TI",true,2L);
    final ContactoDepartamentoAdministrativo c=new ContactoDepartamentoAdministrativo(4,1,"Anexo","618",true,3L);
    void auth(String rol) { when(tokens.esValido("jwt")).thenReturn(true); when(tokens.obtenerEmail("jwt")).thenReturn("a@x.cl");
        when(usuarios.buscarPorEmail("a@x.cl")).thenReturn(Optional.of(new Usuario(1,new Rol(1,rol,null,true,null,null),"A","a@x.cl","h",true,null,null))); }
    @Test void adminCrudDepartamentosSinDelete() throws Exception {
        auth("ADMINISTRADOR"); when(service.listar()).thenReturn(List.of(d)); when(service.buscar(1)).thenReturn(d);
        when(service.crear("TI")).thenReturn(d); when(service.editar(1,"TI",2L)).thenReturn(d); when(service.estado(1,false,2L)).thenReturn(new DepartamentoAdministrativo(1,"TI",false,3L));
        mvc.perform(get("/api/admin/departamentos").header("Authorization","Bearer jwt")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/departamentos/1").header("Authorization","Bearer jwt")).andExpect(status().isOk());
        mvc.perform(post("/api/admin/departamentos").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"TI\"}"))
            .andExpect(status().isCreated()).andExpect(header().string("Location","/api/admin/departamentos/1"));
        mvc.perform(put("/api/admin/departamentos/1").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"TI\",\"version\":2}")).andExpect(status().isOk());
        mvc.perform(patch("/api/admin/departamentos/1/estado").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false,\"version\":2}")).andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        mvc.perform(delete("/api/admin/departamentos/1").header("Authorization","Bearer jwt")).andExpect(status().isMethodNotAllowed());
    }
    @Test void adminCrudContactosYDisponibilidadPadreInactivo() throws Exception {
        auth("ADMINISTRADOR"); when(service.buscar(1)).thenReturn(new DepartamentoAdministrativo(1,"TI",false,2L));
        when(service.contactos(1)).thenReturn(List.of(c)); when(service.contacto(1,4)).thenReturn(c);
        when(service.crearContacto(1,"Anexo","618",false)).thenReturn(c);
        when(service.editarContacto(1,4,"Anexo","619",3L)).thenReturn(c); when(service.estadoContacto(1,4,false,3L)).thenReturn(c);
        mvc.perform(get("/api/admin/departamentos/1/contactos").header("Authorization","Bearer jwt")).andExpect(status().isOk()).andExpect(jsonPath("$[0].disponible").value(false));
        mvc.perform(get("/api/admin/departamentos/1/contactos/4").header("Authorization","Bearer jwt")).andExpect(status().isOk());
        mvc.perform(post("/api/admin/departamentos/1/contactos").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"Anexo\",\"valor\":\"618\",\"activo\":false}"))
            .andExpect(status().isCreated()).andExpect(header().string("Location","/api/admin/departamentos/1/contactos/4"));
        mvc.perform(put("/api/admin/departamentos/1/contactos/4").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"Anexo\",\"valor\":\"619\",\"version\":3}")).andExpect(status().isOk());
        mvc.perform(patch("/api/admin/departamentos/1/contactos/4/estado").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false,\"version\":3}")).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/departamentos/1/contactos/4").header("Authorization","Bearer jwt")).andExpect(status().isMethodNotAllowed());
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"TECNICO","SOPORTE","USUARIO"})
    void otrosRolesNoAdministran(String rol) throws Exception {
        auth(rol);
        for(String ruta:List.of("/api/admin/departamentos","/api/admin/departamentos/1/contactos","/api/admin/responsables")) {
            mvc.perform(get(ruta).header("Authorization","Bearer jwt")).andExpect(status().isForbidden());
            mvc.perform(post(ruta).header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
            mvc.perform(put(ruta+"/4").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
            mvc.perform(patch(ruta+"/4/estado").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service,responsables);
    }
    @Test void sinJwt401() throws Exception { mvc.perform(get("/api/admin/responsables")).andExpect(status().isUnauthorized()); mvc.perform(get("/api/admin/departamentos")).andExpect(status().isUnauthorized()); }
    @Test void tecnicoMantieneLecturaOperacional() throws Exception {
        auth("TECNICO"); when(consultas.listarDepartamentos()).thenReturn(List.of(new Departamento(1,"TI",true)));
        mvc.perform(get("/api/departamentos").header("Authorization","Bearer jwt")).andExpect(status().isOk()).andExpect(jsonPath("$[0].version").doesNotExist());
    }
    @Test void validacion400YCamposAjenos() throws Exception {
        auth("ADMINISTRADOR");
        for(String json:List.of("{\"nombre\":\" \"}","{\"nombre\":\"TI\",\"activo\":false}"))
            mvc.perform(post("/api/admin/departamentos").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/departamentos/1").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"TI\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/departamentos/1/contactos/4").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"Anexo\",\"valor\":\"619\",\"version\":3,\"departamentoId\":9}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service,responsables);
    }
    @Test void conflictos409ConMensaje() throws Exception {
        auth("ADMINISTRADOR"); when(service.estado(1,false,2L)).thenThrow(new OrganizacionConflictoException("Recarga: versión obsoleta"));
        mvc.perform(patch("/api/admin/departamentos/1/estado").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false,\"version\":2}")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Recarga: versión obsoleta"));
    }
    @Test void padreOContactoAjeno404() throws Exception {
        auth("ADMINISTRADOR"); when(service.buscar(99)).thenThrow(new DepartamentoNoEncontradoException(99));
        when(service.contacto(1,4)).thenThrow(new ContactoDepartamentoNoEncontradoException("Ajeno"));
        mvc.perform(get("/api/admin/departamentos/99").header("Authorization","Bearer jwt")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/departamentos/1/contactos/4").header("Authorization","Bearer jwt")).andExpect(status().isNotFound());
    }

    @Test void adminResponsablesCrudSinTrasladoNiDelete() throws Exception {
        auth("ADMINISTRADOR");
        var r=ResponsableAdministrativo.from(new Responsable(4,1,"Ana","TI","00123",true));
        when(responsables.listar(1)).thenReturn(List.of(r));when(responsables.buscar(4)).thenReturn(r);
        when(responsables.crear(1,"Ana","TI","00123")).thenReturn(r);
        when(responsables.editar(4,"Ana","TI","00123",r.revision())).thenReturn(r);
        when(responsables.estado(4,false,r.revision())).thenReturn(r);
        mvc.perform(get("/api/admin/responsables?departamentoId=1").header("Authorization","Bearer jwt")).andExpect(status().isOk()).andExpect(jsonPath("$[0].cargo").value("TI"));
        mvc.perform(get("/api/admin/responsables/4").header("Authorization","Bearer jwt")).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(r.revision()));
        mvc.perform(post("/api/admin/responsables").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"departamentoId\":1,\"nombre\":\"Ana\",\"cargo\":\"TI\",\"contacto\":\"00123\"}")).andExpect(status().isCreated()).andExpect(header().string("Location","/api/admin/responsables/4"));
        String datos="{\"nombre\":\"Ana\",\"cargo\":\"TI\",\"contacto\":\"00123\",\"revision\":\""+r.revision()+"\"}";
        mvc.perform(put("/api/admin/responsables/4").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content(datos)).andExpect(status().isOk());
        mvc.perform(patch("/api/admin/responsables/4/estado").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false,\"revision\":\""+r.revision()+"\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/responsables/4").header("Authorization","Bearer jwt")).andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/admin/responsables/4").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content(datos.replace("{","{\"departamentoId\":2,"))).andExpect(status().isBadRequest());
    }
    @Test void responsablesValidanEntradaYDevuelvenConflicto() throws Exception {
        auth("ADMINISTRADOR");
        for(String json:List.of("{\"nombre\":\"Ana\"}","{\"nombre\":\" \",\"departamentoId\":1}","{\"nombre\":\"Ana\",\"departamentoId\":1,\"activo\":false}"))
            mvc.perform(post("/api/admin/responsables").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/responsables/4").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Ana\"}")).andExpect(status().isBadRequest());
        when(responsables.estado(4,false,"a".repeat(64))).thenThrow(new OrganizacionConflictoException("Recarga el responsable"));
        mvc.perform(patch("/api/admin/responsables/4/estado").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false,\"revision\":\""+"a".repeat(64)+"\"}")).andExpect(status().isConflict());
    }
}
