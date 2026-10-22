package cl.casol.backend;
import cl.casol.backend.conocimiento.application.port.out.ConocimientoRepository;
import cl.casol.backend.conocimiento.application.service.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.infrastructure.web.*;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.infrastructure.security.*;
import cl.casol.backend.procedimiento.application.port.out.ProcedimientoRepository;
import cl.casol.backend.procedimiento.application.service.MantenerPasoService;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.procedimiento.infrastructure.web.PasoController;
import cl.casol.backend.seguimiento.application.service.RegistrarEfectividadSolucionService;
import cl.casol.backend.seguimiento.infrastructure.web.ResultadoSolucionController;
import cl.casol.backend.shared.application.service.VisibilidadContenidoService;
import cl.casol.backend.shared.infrastructure.security.SecurityConfig;
import cl.casol.backend.shared.infrastructure.web.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest({MaterialApoyoController.class,SintomaController.class,MaterialConocimientoArchivoController.class,PasoController.class,ResultadoSolucionController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class,VisibilidadContenidoConfig.class,VisibilidadContenidoService.class})
class ContenidoRestringidoSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean MantenerMaterialApoyoService materiales;
    @MockitoBean MantenerSintomaService sintomas;
    @MockitoBean MaterialConocimientoArchivoService archivos;
    @MockitoBean MantenerPasoService pasos;
    @MockitoBean RegistrarEfectividadSolucionService resultados;
    @MockitoBean ConocimientoRepository conocimientos;
    @MockitoBean ProcedimientoRepository procedimientos;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean TokenServicePort tokens;
    @MockitoBean UsuarioAutenticadoCache cache;
    void autenticar(String rol) {
        when(tokens.esValido("jwt")).thenReturn(true);when(tokens.obtenerEmail("jwt")).thenReturn("u@x.cl");
        when(usuarios.buscarPorEmail("u@x.cl")).thenReturn(Optional.of(new Usuario(1,new Rol(1,rol,null,true,null,null),"U","u@x.cl","h",true,null,null)));
    }
    void conocimiento(EstadoConocimiento estado) {
        when(conocimientos.buscarPorIdIncluidoEliminado(1)).thenReturn(Optional.of(new Conocimiento(1,"T","D",estado,null,null,null,null,null,null,null,null,null)));
    }
    @Test void usuarioNoAccedeAHijosNiArchivosDeBorradorPorApiDirecta() throws Exception {
        autenticar("USUARIO");conocimiento(EstadoConocimiento.BORRADOR);
        for(String ruta:List.of("/api/conocimientos/1/sintomas","/api/conocimientos/1/materiales/2/archivo"))
            mvc.perform(get(ruta).header("Authorization","Bearer jwt")).andExpect(status().isNotFound());
        verifyNoInteractions(sintomas,archivos);
    }
    @Test void staffConsultaBorradorYUsuarioConsultaPublicado() throws Exception {
        autenticar("SOPORTE");conocimiento(EstadoConocimiento.BORRADOR);when(sintomas.listar(1)).thenReturn(List.of());
        mvc.perform(get("/api/conocimientos/1/sintomas").header("Authorization","Bearer jwt")).andExpect(status().isOk());
        autenticar("USUARIO");conocimiento(EstadoConocimiento.PUBLICADO);
        mvc.perform(get("/api/conocimientos/1/sintomas").header("Authorization","Bearer jwt")).andExpect(status().isOk());
    }
    @Test void historicoEliminadoSoloAdministrador() throws Exception {
        conocimiento(EstadoConocimiento.ELIMINADO);when(resultados.listar(1,2)).thenReturn(List.of());
        for(String rol:List.of("USUARIO","TECNICO","SOPORTE")) {
            autenticar(rol);mvc.perform(get("/api/conocimientos/1/soluciones/2/resultados").header("Authorization","Bearer jwt")).andExpect(status().isNotFound());
        }
        autenticar("ADMINISTRADOR");mvc.perform(get("/api/conocimientos/1/soluciones/2/resultados").header("Authorization","Bearer jwt")).andExpect(status().isOk());
    }
    @Test void usuarioNoConsultaPasosDeProcedimientoBorrador() throws Exception {
        autenticar("USUARIO");when(procedimientos.buscarPorId(1)).thenReturn(Optional.of(new Procedimiento(1,"P","D",EstadoProcedimiento.BORRADOR,null,null,null,null)));
        mvc.perform(get("/api/procedimientos/1/pasos").header("Authorization","Bearer jwt")).andExpect(status().isNotFound());verifyNoInteractions(pasos);
    }
    @Test void usuarioNoListaNiDescargaMaterialDeBorrador() throws Exception {
        autenticar("USUARIO"); conocimiento(EstadoConocimiento.BORRADOR);
        for (String ruta:List.of("/api/conocimientos/1/materiales","/api/conocimientos/1/materiales/2/archivo"))
            mvc.perform(get(ruta).header("Authorization","Bearer jwt")).andExpect(status().isNotFound());
        verifyNoInteractions(materiales, archivos);
    }
    @ParameterizedTest @ValueSource(strings={"ADMINISTRADOR","TECNICO","SOPORTE"})
    void personalAutorizadoConservaConsultaMaterialDeBorrador(String rol) throws Exception {
        autenticar(rol); conocimiento(EstadoConocimiento.BORRADOR);
        when(materiales.listar(1)).thenReturn(List.of());
        mvc.perform(get("/api/conocimientos/1/materiales").header("Authorization","Bearer jwt")).andExpect(status().isOk());
        verify(materiales).listar(1);
    }

    @ParameterizedTest @ValueSource(strings={"ADMINISTRADOR","TECNICO","SOPORTE"})
    void personalAutorizadoDescargaArchivoDeBorrador(String rol) throws Exception {
        autenticar(rol); conocimiento(EstadoConocimiento.BORRADOR);
        byte[] pdf="%PDF-1.7\\ncontenido autorizado".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(archivos.descargar(1,2)).thenReturn(new cl.casol.backend.shared.application.archivo.ArchivoDescarga("qa.pdf","application/pdf",pdf));
        mvc.perform(get("/api/conocimientos/1/materiales/2/archivo").header("Authorization","Bearer jwt"))
            .andExpect(status().isOk()).andExpect(content().bytes(pdf));
    }
    @Test void usuarioPuedeDescargarArchivoPublicadoPeroNoAnonimo() throws Exception {
        autenticar("USUARIO"); conocimiento(EstadoConocimiento.PUBLICADO);
        byte[] pdf="%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(archivos.descargar(1,2)).thenReturn(new cl.casol.backend.shared.application.archivo.ArchivoDescarga("qa.pdf","application/pdf",pdf));
        mvc.perform(get("/api/conocimientos/1/materiales/2/archivo").header("Authorization","Bearer jwt"))
            .andExpect(status().isOk()).andExpect(content().bytes(pdf));
        mvc.perform(get("/api/conocimientos/1/materiales/2/archivo")).andExpect(status().isUnauthorized());
    }

}
