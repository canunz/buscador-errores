package cl.casol.backend;

import cl.casol.backend.conocimiento.application.service.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.infrastructure.web.*;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.infrastructure.security.JwtAuthenticationFilter;
import cl.casol.backend.procedimiento.application.service.*;
import cl.casol.backend.procedimiento.infrastructure.web.*;
import cl.casol.backend.shared.application.archivo.*;
import cl.casol.backend.shared.infrastructure.security.SecurityConfig;
import cl.casol.backend.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({MaterialConocimientoArchivoController.class,MaterialPasoArchivoController.class,
        MaterialApoyoController.class,MaterialPasoController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class})
class MaterialArchivoSecurityIntegrationTest {
    static final String CONOCIMIENTO = "/api/conocimientos/1/materiales";
    static final String PASO = "/api/procedimientos/1/pasos/2/materiales";
    static final byte[] PDF = "%PDF-1.7\ncontenido".getBytes();
    static final String REF = "file:materiales/9d50c602-4bf2-438a-82c3-e197c9153250";
    @Autowired MockMvc mvc;
    @MockitoBean MaterialConocimientoArchivoService conocimiento;
    @MockitoBean MaterialPasoArchivoService paso;
    @MockitoBean MantenerMaterialApoyoService enlacesConocimiento;
    @MockitoBean MantenerMaterialPasoService enlacesPaso;
    @MockitoBean TokenServicePort tokens;
    @MockitoBean UsuarioRepository usuarios;

    @ParameterizedTest @ValueSource(strings={"ADMINISTRADOR","TECNICO"})
    void eliminaAmbosDestinos204(String rol) throws Exception {
        auth(rol);
        for(String base : new String[]{CONOCIMIENTO,PASO})
            mvc.perform(delete(base+"/3").header("Authorization","Bearer t"))
                    .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(conocimiento).eliminar(1,3); verify(paso).eliminar(1,2,3);
    }
    @Test void eliminarSinJwt401() throws Exception {
        for(String base : new String[]{CONOCIMIENTO,PASO}) mvc.perform(delete(base+"/3")).andExpect(status().isUnauthorized());
        verifyNoInteractions(conocimiento,paso);
    }
    @Test void eliminarRolAjeno403() throws Exception {
        auth("AUDITOR");
        for(String base : new String[]{CONOCIMIENTO,PASO})
            mvc.perform(delete(base+"/3").header("Authorization","Bearer t")).andExpect(status().isForbidden());
        verifyNoInteractions(conocimiento,paso);
    }
    @Test void falloFisicoEliminacion500() throws Exception {
        auth("TECNICO");
        var ex=new ArchivoException(ArchivoException.Motivo.ALMACENAMIENTO,"Se requiere limpieza operativa");
        doThrow(ex).when(conocimiento).eliminar(1,3); doThrow(ex).when(paso).eliminar(1,2,3);
        for(String base : new String[]{CONOCIMIENTO,PASO})
            mvc.perform(delete(base+"/3").header("Authorization","Bearer t")).andExpect(status().isInternalServerError());
    }
    @Test void eliminarMaterialInexistente404() throws Exception {
        auth("TECNICO");
        var ex=new cl.casol.backend.conocimiento.domain.exception.MaterialApoyoNoEncontradoException(3);
        doThrow(ex).when(conocimiento).eliminar(1,3); doThrow(ex).when(paso).eliminar(1,2,3);
        for(String base : new String[]{CONOCIMIENTO,PASO})
            mvc.perform(delete(base+"/3").header("Authorization","Bearer t")).andExpect(status().isNotFound());
    }
    @Test void eliminarReferenciaInvalida400() throws Exception {
        auth("TECNICO");
        doThrow(new ArchivoException(ArchivoException.Motivo.INVALIDO,"Referencia inválida")).when(conocimiento).eliminar(1,3);
        mvc.perform(delete(CONOCIMIENTO+"/3").header("Authorization","Bearer t")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest @ValueSource(strings={"ADMINISTRADOR","TECNICO"})
    void ambosRolesSubenYDescarganAmbosDestinos(String rol) throws Exception {
        auth(rol);
        when(conocimiento.subir(eq(1),eq("Manual"),eq(TipoMaterial.PDF),any())).thenReturn(new MaterialApoyo(3,1,null,"Manual",TipoMaterial.PDF,REF));
        when(paso.subir(eq(1),eq(2),eq("Manual"),eq(TipoMaterial.PDF),any())).thenReturn(new MaterialApoyo(3,null,2,"Manual",TipoMaterial.PDF,REF));
        when(conocimiento.descargar(1,3)).thenReturn(new ArchivoDescarga("Manual.pdf","application/pdf",PDF));
        when(paso.descargar(1,2,3)).thenReturn(new ArchivoDescarga("Manual.pdf","application/pdf",PDF));
        for (String base : new String[]{CONOCIMIENTO,PASO}) {
            mvc.perform(multipart(base+"/archivo").file(pdf()).param("nombre","Manual").param("tipo","PDF")
                    .header("Authorization","Bearer t"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.url").value(base+"/3/archivo"));
            mvc.perform(get(base+"/3/archivo").header("Authorization","Bearer t"))
                    .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                    .andExpect(content().bytes(PDF))
                    .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;")))
                    .andExpect(header().string("X-Content-Type-Options","nosniff"))
                    .andExpect(header().string("Cache-Control","no-store"));
        }
        verify(conocimiento).subir(eq(1),eq("Manual"),eq(TipoMaterial.PDF),argThat(a -> a.mime().equals("application/pdf") && java.util.Arrays.equals(PDF,a.contenido())));
    }
    @Test void sinJwt401UploadYDescargaAmbosDestinos() throws Exception {
        for(String base : new String[]{CONOCIMIENTO,PASO}) {
            mvc.perform(multipart(base+"/archivo").file(pdf()).param("nombre","M").param("tipo","PDF")).andExpect(status().isUnauthorized());
            mvc.perform(get(base+"/3/archivo")).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(conocimiento,paso);
    }
    @Test void rolAjeno403UploadYDescargaAmbosDestinos() throws Exception {
        auth("AUDITOR");
        for(String base : new String[]{CONOCIMIENTO,PASO}) {
            mvc.perform(multipart(base+"/archivo").file(pdf()).param("nombre","M").param("tipo","PDF").header("Authorization","Bearer t")).andExpect(status().isForbidden());
            mvc.perform(get(base+"/3/archivo").header("Authorization","Bearer t")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(conocimiento,paso);
    }
    @Test void archivoVacio400AntesDelCasoDeUso() throws Exception {
        auth("TECNICO");
        mvc.perform(multipart(CONOCIMIENTO+"/archivo").file(new MockMultipartFile("archivo","a.pdf","application/pdf",new byte[0]))
                .param("nombre","M").param("tipo","PDF").header("Authorization","Bearer t")).andExpect(status().isBadRequest());
        verifyNoInteractions(conocimiento);
    }
    @Test void archivoGrande413AntesDelCasoDeUso() throws Exception {
        auth("TECNICO");
        mvc.perform(multipart(CONOCIMIENTO+"/archivo").file(new MockMultipartFile("archivo","a.pdf","application/pdf",new byte[FormatoArchivo.MAX_BYTES+1]))
                .param("nombre","M").param("tipo","PDF").header("Authorization","Bearer t")).andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(conocimiento);
    }
    @Test void tipoDesconocido400() throws Exception {
        auth("TECNICO");
        mvc.perform(multipart(CONOCIMIENTO+"/archivo").file(pdf()).param("nombre","M").param("tipo","ARCHIVO")
                .header("Authorization","Bearer t")).andExpect(status().isBadRequest());
    }
    @Test void sinArchivo400() throws Exception {
        auth("TECNICO");
        mvc.perform(multipart(CONOCIMIENTO+"/archivo").param("nombre","M").param("tipo","PDF")
                .header("Authorization","Bearer t")).andExpect(status().isBadRequest());
    }
    @Test void erroresDeCasoDeUsoSeMapean() throws Exception {
        auth("TECNICO");
        when(conocimiento.descargar(1,3)).thenThrow(new ArchivoException(ArchivoException.Motivo.NO_ENCONTRADO,"No existe"));
        mvc.perform(get(CONOCIMIENTO+"/3/archivo").header("Authorization","Bearer t")).andExpect(status().isNotFound());
        when(conocimiento.subir(anyInt(),anyString(),any(),any())).thenThrow(new MaxUploadSizeExceededException(10));
        mvc.perform(multipart(CONOCIMIENTO+"/archivo").file(pdf()).param("nombre","M").param("tipo","PDF")
                .header("Authorization","Bearer t")).andExpect(status().isPayloadTooLarge());
    }
    @Test void jsonNoPermiteForjarReferenciaAdministrada() throws Exception {
        auth("TECNICO");
        String json = "{\"nombre\":\"M\",\"tipo\":\"PDF\",\"url\":\""+REF+"\"}";
        for(String base : new String[]{CONOCIMIENTO,PASO}) {
            mvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content(json).header("Authorization","Bearer t")).andExpect(status().isBadRequest());
            mvc.perform(put(base+"/3").contentType(MediaType.APPLICATION_JSON).content(json).header("Authorization","Bearer t")).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(enlacesConocimiento,enlacesPaso);
    }
    @Test void enlacesExternosConservanUrl() throws Exception {
        auth("TECNICO");
        String url = "https://ejemplo.cl/video";
        when(enlacesConocimiento.crear(1,"M",TipoMaterial.VIDEO,url)).thenReturn(new MaterialApoyo(3,1,null,"M",TipoMaterial.VIDEO,url));
        when(enlacesPaso.crear(1,2,"M",TipoMaterial.VIDEO,url)).thenReturn(new MaterialApoyo(3,null,2,"M",TipoMaterial.VIDEO,url));
        for(String base : new String[]{CONOCIMIENTO,PASO})
            mvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"M\",\"tipo\":\"VIDEO\",\"url\":\""+url+"\"}")
                    .header("Authorization","Bearer t")).andExpect(status().isCreated()).andExpect(jsonPath("$.url").value(url));
    }
    private MockMultipartFile pdf() { return new MockMultipartFile("archivo","../../cliente.pdf","application/pdf",PDF); }
    private void auth(String rol) {
        when(tokens.esValido("t")).thenReturn(true); when(tokens.obtenerEmail("t")).thenReturn("u@x.cl");
        when(usuarios.buscarPorEmail("u@x.cl")).thenReturn(Optional.of(new Usuario(1,new Rol(1,rol,null,true,null,null),"U","u@x.cl","h",true,null,null)));
    }
}
