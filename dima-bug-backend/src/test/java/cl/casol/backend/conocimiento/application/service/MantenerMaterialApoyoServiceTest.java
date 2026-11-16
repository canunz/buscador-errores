package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MantenerMaterialApoyoServiceTest {
    ConocimientoRepository conocimientos; MaterialApoyoRepository materiales; IndexarConocimientoService indexador; MantenerMaterialApoyoService service;
    @BeforeEach void setUp() { conocimientos=mock(ConocimientoRepository.class); materiales=mock(MaterialApoyoRepository.class);
        indexador=mock(IndexarConocimientoService.class); service=new MantenerMaterialApoyoService(conocimientos,materiales,indexador); }
    void existe() { when(conocimientos.buscarPorId(4)).thenReturn(Optional.of(mock(Conocimiento.class))); }
    @Test void listaMaterialesDirectos() { existe(); when(materiales.buscarDirectosPorConocimiento(4)).thenReturn(List.of(
            new MaterialApoyo(1,4,null,"Manual",TipoMaterial.PDF,"manual.pdf")));
        assertEquals(1,service.listar(4).size()); verify(materiales).buscarDirectosPorConocimiento(4); }
    @Test void listaVacia() { existe(); when(materiales.buscarDirectosPorConocimiento(4)).thenReturn(List.of()); assertTrue(service.listar(4).isEmpty()); }
    @Test void listarConocimientoInexistente() { when(conocimientos.buscarPorId(4)).thenReturn(Optional.empty());
        assertThrows(ConocimientoNoEncontradoException.class,()->service.listar(4)); verifyNoInteractions(materiales); }
    @Test void creaCadaTipoConPasoNulo() { existe(); when(materiales.guardar(any())).thenAnswer(i->i.getArgument(0));
        for(TipoMaterial tipo:TipoMaterial.values()) { MaterialApoyo m=service.crear(4,"Material",tipo,"url");
            assertEquals(tipo,m.tipo()); assertNull(m.pasoId()); assertEquals(4,m.conocimientoId()); } }
    @Test void crearConocimientoInexistente() { when(conocimientos.buscarPorId(4)).thenReturn(Optional.empty());
        assertThrows(ConocimientoNoEncontradoException.class,()->service.crear(4,"M",TipoMaterial.PDF,"url")); }
    @Test void modificaMaterialDirecto() { existe(); when(materiales.buscarPorId(2)).thenReturn(Optional.of(
            new MaterialApoyo(2,4,null,"Viejo",TipoMaterial.IMAGEN,"a"))); when(materiales.guardar(any())).thenAnswer(i->i.getArgument(0));
        assertEquals(new MaterialApoyo(2,4,null,"Nuevo",TipoMaterial.PDF,"b"),service.modificar(4,2,"Nuevo",TipoMaterial.PDF,"b")); verify(indexador).indexar(4); }
    @Test void modificarMaterialInexistente() { existe(); when(materiales.buscarPorId(2)).thenReturn(Optional.empty());
        assertThrows(MaterialApoyoNoEncontradoException.class,()->service.modificar(4,2,"N",TipoMaterial.PDF,"u")); }
    @Test void modificarMaterialOtroConocimiento() { existe(); when(materiales.buscarPorId(2)).thenReturn(Optional.of(
            new MaterialApoyo(2,9,null,"M",TipoMaterial.PDF,"u")));
        assertThrows(MaterialApoyoNoEncontradoException.class,()->service.modificar(4,2,"N",TipoMaterial.PDF,"u")); verify(materiales,never()).guardar(any()); }
    @Test void modificarMaterialDePaso() { existe(); when(materiales.buscarPorId(2)).thenReturn(Optional.of(
            new MaterialApoyo(2,null,10,"M",TipoMaterial.PDF,"u")));
        assertThrows(MaterialApoyoNoEncontradoException.class,()->service.modificar(4,2,"N",TipoMaterial.PDF,"u")); verify(materiales,never()).guardar(any()); }
}
