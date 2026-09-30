package cl.casol.backend.procedimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.MaterialApoyoRepository;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.MaterialApoyoNoEncontradoException;
import cl.casol.backend.procedimiento.application.port.out.*;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.procedimiento.domain.exception.*;
import org.junit.jupiter.api.*; import org.mockito.*; import java.util.*;
import static org.junit.jupiter.api.Assertions.*; import static org.mockito.Mockito.*;

class MantenerMaterialPasoServiceTest {
    @Mock ProcedimientoRepository procedimientos; @Mock PasoRepository pasos; @Mock MaterialApoyoRepository materiales;
    MantenerMaterialPasoService service;
    @BeforeEach void setUp(){MockitoAnnotations.openMocks(this);service=new MantenerMaterialPasoService(procedimientos,pasos,materiales);when(procedimientos.buscarPorId(1)).thenReturn(Optional.of(mock(Procedimiento.class)));when(pasos.buscarPorId(5)).thenReturn(Optional.of(new Paso(5,1,1,"X",false)));when(materiales.guardar(any())).thenAnswer(i->i.getArgument(0));}
    @Test void creaTodosLosTiposConXorCorrecto(){for(TipoMaterial tipo:TipoMaterial.values()){MaterialApoyo m=service.crear(1,5,"N",tipo,"url");assertNull(m.conocimientoId());assertEquals(5,m.pasoId());assertEquals(tipo,m.tipo());}}
    @Test void listaMaterialesDelPaso(){when(materiales.buscarPorPaso(5)).thenReturn(List.of(materialPaso(8)));assertEquals(1,service.listar(1,5).size());verify(materiales).buscarPorPaso(5);}
    @Test void modificaSinMoverDestino(){when(materiales.buscarPorId(8)).thenReturn(Optional.of(materialPaso(8)));MaterialApoyo m=service.modificar(1,5,8,"Nuevo",TipoMaterial.PDF,"u2");assertNull(m.conocimientoId());assertEquals(5,m.pasoId());}
    @Test void noModificaMaterialDeOtroPaso(){when(materiales.buscarPorId(8)).thenReturn(Optional.of(new MaterialApoyo(8,null,6,"N",TipoMaterial.PDF,"u")));assertThrows(MaterialApoyoNoEncontradoException.class,()->service.modificar(1,5,8,"X",TipoMaterial.PDF,"u"));}
    @Test void noModificaMaterialDeConocimiento(){when(materiales.buscarPorId(8)).thenReturn(Optional.of(new MaterialApoyo(8,9,null,"N",TipoMaterial.PDF,"u")));assertThrows(MaterialApoyoNoEncontradoException.class,()->service.modificar(1,5,8,"X",TipoMaterial.PDF,"u"));}
    @Test void rechazaPasoDeOtroProcedimiento(){when(pasos.buscarPorId(5)).thenReturn(Optional.of(new Paso(5,2,1,"X",false)));assertThrows(PasoNoEncontradoException.class,()->service.listar(1,5));}
    @Test void procedimientoInexistenteTienePrioridad(){assertThrows(ProcedimientoNoEncontradoException.class,()->service.listar(404,5));verify(pasos,never()).buscarPorId(any());}
    private MaterialApoyo materialPaso(int id){return new MaterialApoyo(id,null,5,"N",TipoMaterial.IMAGEN,"u");}
}
