package cl.casol.backend.catalogo.application.service;
import cl.casol.backend.catalogo.application.port.out.*;
import cl.casol.backend.catalogo.domain.*;
import cl.casol.backend.catalogo.domain.exception.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AdministrarCatalogosServiceTest {
    CatalogoAdministrativoRepository repo=mock(CatalogoAdministrativoRepository.class);
    AdministrarCatalogosService service=new AdministrarCatalogosService(repo);
    CatalogoAdministrativo r(Integer id,String nombre,boolean activo,Integer sistemaId) { return new CatalogoAdministrativo(id,nombre,activo,null,null,sistemaId,1,List.of()); }
    @Test void creaNormalizaYReindexa() {
        when(repo.guardar(eq(TipoCatalogo.hardware),any())).thenAnswer(i -> {var d=(CatalogoAdministrativo)i.getArgument(1);return r(1,d.nombre(),d.activo(),null);});
        var creado=service.guardar(TipoCatalogo.hardware,null,r(null," POS ",true,null));
        assertEquals("POS",creado.nombre());verify(repo).bloquear(TipoCatalogo.hardware);verify(repo).reindexarReferencias(TipoCatalogo.hardware,1);
    }
    @Test void duplicadosIncluyenInactivos() {
        when(repo.listar(TipoCatalogo.frecuencias)).thenReturn(List.of(r(1,"Diario",false,null)));
        assertThrows(CatalogoConflictoException.class,()->service.guardar(TipoCatalogo.frecuencias,null,r(null," diario ",true,null)));
        verify(repo,never()).guardar(any(),any());
    }
    @Test void editaSinDuplicarseYConservaId() {
        var actual=r(1,"POS",true,null); when(repo.buscar(TipoCatalogo.hardware,1)).thenReturn(Optional.of(actual));
        when(repo.listar(TipoCatalogo.hardware)).thenReturn(List.of(actual)); when(repo.guardar(any(),any())).thenAnswer(i->i.getArgument(1));
        assertEquals(1,service.guardar(TipoCatalogo.hardware,1,r(1,"POS",true,null)).id());
    }
    @Test void estadoEsPersistenteYNoElimina() {
        when(repo.buscar(TipoCatalogo.hardware,1)).thenReturn(Optional.of(r(1,"POS",true,null)));
        when(repo.guardar(any(),any())).thenAnswer(i->i.getArgument(1));
        assertFalse(service.cambiarEstado(TipoCatalogo.hardware,1,false).activo());
    }
    @Test void moduloNecesitaSistemaActivo() {
        when(repo.buscar(TipoCatalogo.sistemas,2)).thenReturn(Optional.of(r(2,"ERP",false,null)));
        assertThrows(CatalogoInvalidoException.class,()->service.guardar(TipoCatalogo.modulos,null,r(null,"Caja",true,2)));
    }
    @Test void noMueveModuloReferenciado() {
        when(repo.buscar(TipoCatalogo.modulos,1)).thenReturn(Optional.of(r(1,"Caja",true,2)));
        when(repo.buscar(TipoCatalogo.sistemas,3)).thenReturn(Optional.of(r(3,"ERP",true,null)));
        when(repo.moduloReferenciado(1)).thenReturn(true);
        assertThrows(CatalogoConflictoException.class,()->service.guardar(TipoCatalogo.modulos,1,r(1,"Caja",true,3)));
    }
    @Test void asociacionHardwareSistemaRequiereExistencia() {
        var datos=new CatalogoAdministrativo(null,"POS",true,null,null,null,null,List.of(99));
        assertThrows(CatalogoNoEncontradoException.class,()->service.guardar(TipoCatalogo.hardware,null,datos));
    }
    CatalogoAdministrativo hardwareCon(List<Integer> ids) {
        return new CatalogoAdministrativo(1,"POS",true,"Windows",null,null,null,ids);
    }
    void hardwareExistente() {
        when(repo.buscar(TipoCatalogo.hardware,1)).thenReturn(Optional.of(hardwareCon(List.of(2,3))));
        when(repo.buscar(TipoCatalogo.sistemas,2)).thenReturn(Optional.of(r(2,"ERP",true,null)));
        when(repo.buscar(TipoCatalogo.sistemas,3)).thenReturn(Optional.of(r(3,"Legacy",false,null)));
        when(repo.guardar(eq(TipoCatalogo.hardware),any())).thenAnswer(i->i.getArgument(1));
    }
    @Test void putConListaOmitidaConservaAsociacionesInclusoInactivas() {
        hardwareExistente();
        var resultado=service.guardar(TipoCatalogo.hardware,1,hardwareCon(null));
        assertEquals(List.of(2,3),resultado.sistemaIds());
        verify(repo).reindexarReferencias(TipoCatalogo.hardware,1);
    }
    @Test void putConListaVaciaDesvinculaExplicitamente() {
        hardwareExistente();
        assertEquals(List.of(),service.guardar(TipoCatalogo.hardware,1,hardwareCon(List.of())).sistemaIds());
    }
    @Test void putConValoresReemplazaLaListaYConservaUnaInactivaOriginal() {
        hardwareExistente();
        assertEquals(List.of(3),service.guardar(TipoCatalogo.hardware,1,hardwareCon(List.of(3))).sistemaIds());
    }
    @Test void postConListaOmitidaComienzaSinAsociaciones() {
        when(repo.guardar(eq(TipoCatalogo.hardware),any())).thenAnswer(i->i.getArgument(1));
        assertTrue(service.guardar(TipoCatalogo.hardware,null,hardwareCon(null)).sistemaIds().isEmpty());
    }
    @Test void nuevosSistemasInactivosNoSeAsocian() {
        hardwareExistente();
        when(repo.buscar(TipoCatalogo.sistemas,4)).thenReturn(Optional.of(r(4,"Otro",false,null)));
        assertThrows(CatalogoInvalidoException.class,()->service.guardar(TipoCatalogo.hardware,1,hardwareCon(List.of(2,4))));
        verify(repo,never()).guardar(any(),any());
    }
    @Test void duplicadosNoSeGuardan() {
        hardwareExistente();
        assertThrows(CatalogoInvalidoException.class,()->service.guardar(TipoCatalogo.hardware,1,hardwareCon(List.of(2,2))));
        verify(repo,never()).guardar(any(),any());
    }
    @Test void inexistenteEnPutNoSeGuarda() {
        hardwareExistente();
        assertThrows(CatalogoNoEncontradoException.class,()->service.guardar(TipoCatalogo.hardware,1,hardwareCon(List.of(99))));
        verify(repo,never()).guardar(any(),any());
    }
}
