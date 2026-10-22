package cl.casol.backend;

import cl.casol.backend.catalogo.application.port.out.*;
import cl.casol.backend.catalogo.application.service.AdministrarCatalogosService;
import cl.casol.backend.catalogo.domain.*;
import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.application.service.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.conocimiento.infrastructure.web.ConocimientoController;
import cl.casol.backend.catalogo.infrastructure.web.AdminCatalogoController;
import cl.casol.backend.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class LimitesCompatibilidadStagingTest {
    CatalogoAdministrativoRepository catalogos = mock(CatalogoAdministrativoRepository.class);
    ConocimientoRepository conocimientos = mock(ConocimientoRepository.class);
    UsuarioRepository usuarios = mock(UsuarioRepository.class);
    IndexarConocimientoService indice = mock(IndexarConocimientoService.class);
    MantenerConocimientoService conocimiento = new MantenerConocimientoService(conocimientos,
        mock(HardwareRepository.class), mock(SistemaRepository.class), mock(ModuloRepository.class),
        mock(FrecuenciaRepository.class), usuarios, indice, mock(SolucionRepository.class));
    AdministrarCatalogosService catalogo = new AdministrarCatalogosService(catalogos);
    MockMvc mvc;
    final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
        "qa@example.invalid", "", List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));
    CatalogoAdministrativo frecuencia(int longitud) {
        return new CatalogoAdministrativo(1,"F".repeat(longitud),true,null,null,null,1,List.of());
    }
    Conocimiento existente(int longitud) {
        return new Conocimiento(1,"T","D",EstadoConocimiento.BORRADOR,null,null,null,null,
            "C".repeat(longitud),mock(cl.casol.backend.identidad.domain.Usuario.class),null,null,null);
    }
    @BeforeEach void preparar() {
        when(catalogos.buscar(TipoCatalogo.frecuencias,1)).thenReturn(Optional.of(frecuencia(50)));
        when(catalogos.guardar(any(),any())).thenAnswer(i -> i.getArgument(1));
        when(conocimientos.buscarPorId(1)).thenReturn(Optional.of(existente(500)));
        when(conocimientos.buscarPorIdIncluidoEliminado(1)).thenReturn(Optional.of(existente(500)));
        when(conocimientos.guardar(any())).thenAnswer(i -> i.getArgument(0));
        when(usuarios.buscarPorEmail(anyString())).thenReturn(Optional.of(mock(cl.casol.backend.identidad.domain.Usuario.class)));
        mvc = MockMvcBuilders.standaloneSetup(new AdminCatalogoController(catalogo),
            new ConocimientoController(conocimiento,mock(BuscarConocimientoService.class)))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void frecuenciaPost50Aceptado51Es400SinPersistir() throws Exception {
        for (int n : new int[]{50,51}) {
            clearInvocations(catalogos);
            mvc.perform(post("/api/admin/catalogos/frecuencias").contentType("application/json")
                .content("{\"nombre\":\""+"F".repeat(n)+"\",\"activo\":true,\"orden\":1}"))
                .andExpect(status().is(n==50?201:400));
            if(n==51) verify(catalogos,never()).guardar(any(),any());
        }
    }
    @Test void frecuenciaPut50Aceptado51Es400SinPersistir() throws Exception {
        for (int n : new int[]{50,51}) {
            clearInvocations(catalogos);
            mvc.perform(put("/api/admin/catalogos/frecuencias/1").contentType("application/json")
                .content("{\"nombre\":\""+"F".repeat(n)+"\",\"activo\":true,\"orden\":1}"))
                .andExpect(status().is(n==50?200:400));
            if(n==51) verify(catalogos,never()).guardar(any(),any());
        }
    }
    @Test void frecuenciaEstadoTambienValidaNombreExistente() throws Exception {
        for(int n : new int[]{50,51}) {
            when(catalogos.buscar(TipoCatalogo.frecuencias,1)).thenReturn(Optional.of(frecuencia(n)));
            clearInvocations(catalogos);
            mvc.perform(patch("/api/admin/catalogos/frecuencias/1/estado").contentType("application/json")
                .content("{\"activo\":false}")).andExpect(status().is(n==50?200:400));
            if(n==51) verify(catalogos,never()).guardar(any(),any());
        }
    }
    @Test void comentarioPost500Aceptado501Es400SinPersistir() throws Exception {
        comentarioHttp(false);
    }
    @Test void comentarioPut500Aceptado501Es400SinPersistir() throws Exception {
        comentarioHttp(true);
    }
    void comentarioHttp(boolean editar) throws Exception {
        for(int n : new int[]{500,501}) {
            clearInvocations(conocimientos);
            mvc.perform((editar?put("/api/conocimientos/1"):post("/api/conocimientos"))
                .principal(auth).contentType("application/json")
                .content("{\"titulo\":\"T\",\"descripcion\":\"D\",\"comentario\":\""+"C".repeat(n)+"\"}"))
                .andExpect(status().is(n==501?400:editar?200:201));
            if(n==501) verify(conocimientos,never()).guardar(any());
        }
    }
    @Test void servicioCrearRechaza501SinDependerDelDto() {
        assertThrows(cl.casol.backend.conocimiento.domain.exception.ClasificacionInvalidaException.class,
            () -> conocimiento.crear("T","D",null,null,null,null,"C".repeat(501),auth.getName()));
        verify(conocimientos,never()).guardar(any());
    }
    @Test void servicioModificarRechaza501SinDependerDelDto() {
        assertThrows(cl.casol.backend.conocimiento.domain.exception.ClasificacionInvalidaException.class,
            () -> conocimiento.modificar(1,"T","D",null,null,null,null,"C".repeat(501),auth.getName()));
        verify(conocimientos,never()).guardar(any());
    }
    @Test void estadoComentario500Aceptado501Es400() throws Exception {
        for(int n : new int[]{500,501}) {
            when(conocimientos.buscarPorId(1)).thenReturn(Optional.of(existente(n)));
            clearInvocations(conocimientos);
            mvc.perform(patch("/api/conocimientos/1/estado").principal(auth).contentType("application/json")
                .content("{\"estado\":\"BORRADOR\"}")).andExpect(status().is(n==500?200:400));
            if(n==501) verify(conocimientos,never()).guardar(any());
        }
    }
    @Test void eliminarComentario500Aceptado501Es400() throws Exception {
        for(int n : new int[]{500,501}) {
            when(conocimientos.buscarPorIdIncluidoEliminado(1)).thenReturn(Optional.of(existente(n)));
            clearInvocations(conocimientos);
            mvc.perform(delete("/api/conocimientos/1").principal(auth)).andExpect(status().is(n==500?204:400));
            if(n==501) verify(conocimientos,never()).guardar(any());
        }
    }
    @Test void comentarioNuloSiguePermitido() {
        assertNull(conocimiento.crear("T","D",null,null,null,null,null,auth.getName()).getComentario());
    }
}
