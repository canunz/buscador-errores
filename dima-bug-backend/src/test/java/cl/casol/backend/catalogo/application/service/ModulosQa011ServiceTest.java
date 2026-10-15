package cl.casol.backend.catalogo.application.service;
import cl.casol.backend.catalogo.application.port.out.CatalogoAdministrativoRepository;
import cl.casol.backend.catalogo.domain.*;
import cl.casol.backend.catalogo.domain.exception.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModulosQa011ServiceTest {
    CatalogoAdministrativoRepository repo=mock(CatalogoAdministrativoRepository.class);
    AdministrarCatalogosService service=new AdministrarCatalogosService(repo);
    CatalogoAdministrativo modulo(Integer id,String nombre,boolean activo,Integer sistema) {
        return new CatalogoAdministrativo(id,nombre,activo,null,null,sistema,null,List.of());
    }
    void sistema(int id,boolean activo) {
        when(repo.buscar(TipoCatalogo.sistemas,id)).thenReturn(Optional.of(modulo(id,"Sistema",activo,null)));
    }
    @BeforeEach void setup() { when(repo.guardar(any(),any())).thenAnswer(i->i.getArgument(1)); }
    @Test void creaModuloValidoNormalizaNombre() {
        sistema(2,true); var r=service.guardar(TipoCatalogo.modulos,null,modulo(null," Caja ",true,2));
        assertEquals("Caja",r.nombre()); assertEquals(2,r.sistemaId());
        var orden=inOrder(repo); orden.verify(repo).bloquear(TipoCatalogo.sistemas); orden.verify(repo).bloquear(TipoCatalogo.modulos);
    }
    @Test void nombresVaciosYExcesivosRechazados() {
        for(String n:Arrays.asList(null,"","  ","x".repeat(121)))
            assertThrows(CatalogoInvalidoException.class,()->service.guardar(TipoCatalogo.modulos,null,modulo(null,n,true,2)));
        verify(repo,never()).guardar(any(),any());
    }
    @Test void nombre120Permitido() { sistema(2,true); assertEquals(120,service.guardar(TipoCatalogo.modulos,null,modulo(null,"x".repeat(120),true,2)).nombre().length()); }
    @Test void duplicadosMismoSistemaIncluyenInactivos() {
        when(repo.listar(TipoCatalogo.modulos)).thenReturn(List.of(modulo(1," Caja ",false,2)));
        assertThrows(CatalogoConflictoException.class,()->service.guardar(TipoCatalogo.modulos,null,modulo(null,"caja",true,2)));
        verify(repo,never()).guardar(any(),any());
    }
    @Test void nombreIgualEnOtroSistemaPermitido() {
        sistema(3,true); when(repo.listar(TipoCatalogo.modulos)).thenReturn(List.of(modulo(1,"Caja",true,2)));
        assertEquals(3,service.guardar(TipoCatalogo.modulos,null,modulo(null,"Caja",true,3)).sistemaId());
    }
    @Test void editaMismoIdSinDeclararloDuplicado() {
        sistema(2,true); var m=modulo(1,"Caja",true,2);
        when(repo.buscar(TipoCatalogo.modulos,1)).thenReturn(Optional.of(m)); when(repo.listar(TipoCatalogo.modulos)).thenReturn(List.of(m));
        assertEquals(1,service.guardar(TipoCatalogo.modulos,1,m).id());
    }
    @Test void sistemaObligatorioEInexistente() {
        assertThrows(CatalogoInvalidoException.class,()->service.guardar(TipoCatalogo.modulos,null,modulo(null,"Caja",true,null)));
        assertThrows(CatalogoNoEncontradoException.class,()->service.guardar(TipoCatalogo.modulos,null,modulo(null,"Caja",true,99)));
    }
    @Test void sistemaInactivoRechazaAltaYTraslado() {
        sistema(3,false); when(repo.buscar(TipoCatalogo.modulos,1)).thenReturn(Optional.of(modulo(1,"Caja",true,2)));
        assertThrows(CatalogoInvalidoException.class,()->service.guardar(TipoCatalogo.modulos,null,modulo(null,"Caja",true,3)));
        assertThrows(CatalogoInvalidoException.class,()->service.guardar(TipoCatalogo.modulos,1,modulo(1,"Caja",true,3)));
    }
    @Test void permiteConservarSistemaInactivoPeroNoReactivarModulo() {
        sistema(3,false); when(repo.buscar(TipoCatalogo.modulos,1)).thenReturn(Optional.of(modulo(1,"Caja",false,3)));
        assertFalse(service.guardar(TipoCatalogo.modulos,1,modulo(1,"Nuevo",false,3)).activo());
        assertThrows(CatalogoInvalidoException.class,()->service.cambiarEstado(TipoCatalogo.modulos,1,true));
    }
    @Test void trasladoReferenciadoNoGuarda() {
        sistema(3,true); when(repo.buscar(TipoCatalogo.modulos,1)).thenReturn(Optional.of(modulo(1,"Caja",true,2)));
        when(repo.moduloReferenciado(1)).thenReturn(true);
        assertThrows(CatalogoConflictoException.class,()->service.guardar(TipoCatalogo.modulos,1,modulo(1,"Caja",true,3)));
        verify(repo,never()).guardar(any(),any());
    }
    @Test void trasladoSinUsoEsPermitido() {
        sistema(3,true); when(repo.buscar(TipoCatalogo.modulos,1)).thenReturn(Optional.of(modulo(1,"Caja",true,2)));
        assertEquals(3,service.guardar(TipoCatalogo.modulos,1,modulo(1,"Caja",true,3)).sistemaId());
    }
    @Test void desactivaUtilizadoConservandoIdSistemaYReindexa() {
        sistema(2,true); when(repo.buscar(TipoCatalogo.modulos,1)).thenReturn(Optional.of(modulo(1,"Caja",true,2)));
        var r=service.cambiarEstado(TipoCatalogo.modulos,1,false);
        assertEquals(1,r.id()); assertEquals(2,r.sistemaId()); assertFalse(r.activo());
        verify(repo,never()).moduloReferenciado(any()); verify(repo).reindexarReferencias(TipoCatalogo.modulos,1);
    }
}
