package cl.casol.backend;

import cl.casol.backend.identidad.application.service.AdministrarSucursalesService;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.Sucursal;
import cl.casol.backend.identidad.infrastructure.security.*;
import cl.casol.backend.identidad.infrastructure.web.*;
import cl.casol.backend.shared.infrastructure.security.SecurityConfig;
import cl.casol.backend.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest({AdminSucursalController.class,SucursalController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class})
class SucursalSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdministrarSucursalesService service;
    @MockitoBean TokenServicePort tokens;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean UsuarioAutenticadoCache cache;
    @MockitoBean cl.casol.backend.shared.application.service.VisibilidadContenidoService visibilidad;
    Sucursal sucursal() { return new Sucursal(1,"QA_01","Sintética","Responsable QA","qa-no-enviar@dimarsa.cl",true,0L); }
    @Test void sinAutenticacionNoExponeDirectorio() throws Exception {
        mvc.perform(get("/api/sucursales")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/sucursales")).andExpect(status().isUnauthorized());verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"TECNICO","SOPORTE","USUARIO"})
    void soloAdminPuedeAdministrar(String rol) throws Exception {
        mvc.perform(get("/api/admin/sucursales").with(user("qa").roles(rol))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/sucursales").with(user("qa").roles(rol)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/sucursales/1").with(user("qa").roles(rol)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/sucursales/1/estado").with(user("qa").roles(rol)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"ADMINISTRADOR","TECNICO","SOPORTE"})
    void consultaOperacionalNoExponeCorreoNiVersion(String rol) throws Exception {
        when(service.disponibles()).thenReturn(List.of(sucursal()));
        mvc.perform(get("/api/sucursales").with(user("qa").roles(rol))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("QA_01"))
                .andExpect(jsonPath("$[0].responsableEmail").doesNotExist()).andExpect(jsonPath("$[0].version").doesNotExist());
    }
    @Test void usuarioFinalNoConsultaDirectorioOperacional() throws Exception {
        mvc.perform(get("/api/sucursales").with(user("qa").roles("USUARIO"))).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void adminConsultaYNoExisteBorradoFisico() throws Exception {
        when(service.listar()).thenReturn(List.of(sucursal()));
        mvc.perform(get("/api/admin/sucursales").with(user("qa").roles("ADMINISTRADOR"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].version").value(0));
        mvc.perform(delete("/api/admin/sucursales/1").with(user("qa").roles("ADMINISTRADOR"))).andExpect(status().isMethodNotAllowed());
    }
    @Test void versionDeEstadoEsObligatoriaAntesDeInvocarServicio() throws Exception {
        mvc.perform(patch("/api/admin/sucursales/1/estado").with(user("qa").roles("ADMINISTRADOR")).contentType(MediaType.APPLICATION_JSON).content("{\"activa\":false}"))
                .andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
}
