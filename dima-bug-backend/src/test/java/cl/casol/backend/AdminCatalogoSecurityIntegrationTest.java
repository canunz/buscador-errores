package cl.casol.backend;
import cl.casol.backend.catalogo.application.service.AdministrarCatalogosService;
import cl.casol.backend.catalogo.domain.*;
import cl.casol.backend.catalogo.infrastructure.web.AdminCatalogoController;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.infrastructure.security.*;
import cl.casol.backend.shared.infrastructure.security.SecurityConfig;
import cl.casol.backend.shared.infrastructure.web.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(AdminCatalogoController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class})
class AdminCatalogoSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdministrarCatalogosService service;
    @MockitoBean TokenServicePort tokenService;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean UsuarioAutenticadoCache cache;
    @MockitoBean cl.casol.backend.shared.application.service.VisibilidadContenidoService visibilidad;
    String cuerpo="{\"nombre\":\"POS\",\"activo\":true,\"orden\":1,\"sistemaId\":1,\"sistemaIds\":[1]}";
    void autenticar(String rol) {
        when(tokenService.esValido("jwt")).thenReturn(true); when(tokenService.obtenerEmail("jwt")).thenReturn("a@x.cl");
        when(usuarios.buscarPorEmail("a@x.cl")).thenReturn(Optional.of(new Usuario(1,new Rol(1,rol,null,true,null,null),"A","a@x.cl","h",true,null,null)));
    }
    @Test void administradorPuedeConsultarCrearEditarYDesactivarLosCuatroCatalogos() throws Exception {
        autenticar("ADMINISTRADOR");
        for(TipoCatalogo tipo:TipoCatalogo.values()) {
            String base="/api/admin/catalogos/"+tipo;
            CatalogoAdministrativo r=new CatalogoAdministrativo(1,"POS",true,null,null,1,1,List.of(1));
            when(service.listar(tipo)).thenReturn(List.of(r)); when(service.buscar(tipo,1)).thenReturn(r);
            when(service.guardar(eq(tipo),any(),any())).thenReturn(r); when(service.cambiarEstado(tipo,1,false)).thenReturn(r);
            mvc.perform(get(base).header("Authorization","Bearer jwt")).andExpect(status().isOk());
            mvc.perform(get(base+"/1").header("Authorization","Bearer jwt")).andExpect(status().isOk());
            mvc.perform(post(base).header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isCreated());
            mvc.perform(put(base+"/1").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isOk());
            mvc.perform(patch(base+"/1/estado").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")).andExpect(status().isOk());
            mvc.perform(delete(base+"/1").header("Authorization","Bearer jwt")).andExpect(status().isMethodNotAllowed());
        }
    }
    @Test void otrosRolesNoPuedenAdministrar() throws Exception {
        for(String rol:List.of("TECNICO","SOPORTE","USUARIO","AUDITOR")) {
            autenticar(rol);
            for(TipoCatalogo tipo:TipoCatalogo.values()) {
                String base="/api/admin/catalogos/"+tipo;
                mvc.perform(get(base).header("Authorization","Bearer jwt")).andExpect(status().isForbidden());
                mvc.perform(post(base).header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
                mvc.perform(put(base+"/1").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
                mvc.perform(patch(base+"/1/estado").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(service);
    }
    @Test void sinJwt401() throws Exception { mvc.perform(get("/api/admin/catalogos/hardware")).andExpect(status().isUnauthorized()); }
    @Test void nombreVacio400() throws Exception { autenticar("ADMINISTRADOR"); mvc.perform(post("/api/admin/catalogos/hardware").header("Authorization","Bearer jwt").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\" \",\"activo\":true}")).andExpect(status().isBadRequest()); }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"omitida","vacia","valores"})
    void putHardwareDistingueListaOmitidaVaciaYConValores(String caso) throws Exception {
        autenticar("ADMINISTRADOR");
        String extra=switch(caso) {
            case "vacia" -> ",\"sistemaIds\":[]";
            case "valores" -> ",\"sistemaIds\":[2,3]";
            default -> "";
        };
        var r=new CatalogoAdministrativo(1,"POS",true,null,null,null,null,List.of(2,3));
        when(service.guardar(eq(TipoCatalogo.hardware),eq(1),any())).thenReturn(r);
        mvc.perform(put("/api/admin/catalogos/hardware/1").header("Authorization","Bearer jwt")
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"POS\",\"activo\":true"+extra+"}"))
                .andExpect(status().isOk());
        var captor=org.mockito.ArgumentCaptor.forClass(CatalogoAdministrativo.class);
        verify(service).guardar(eq(TipoCatalogo.hardware),eq(1),captor.capture());
        if(caso.equals("omitida")) org.junit.jupiter.api.Assertions.assertNull(captor.getValue().sistemaIds());
        else org.junit.jupiter.api.Assertions.assertEquals(caso.equals("vacia")?List.of():List.of(2,3),captor.getValue().sistemaIds());
    }
}
