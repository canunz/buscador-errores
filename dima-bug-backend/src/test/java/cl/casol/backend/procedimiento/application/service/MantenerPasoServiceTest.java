package cl.casol.backend.procedimiento.application.service;

import cl.casol.backend.procedimiento.application.port.out.*;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.procedimiento.domain.exception.*;
import org.junit.jupiter.api.*; import org.mockito.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*; import static org.mockito.Mockito.*;

class MantenerPasoServiceTest {
    @Mock ProcedimientoRepository procedimientos; @Mock PasoRepository pasos; MantenerPasoService service;
    @BeforeEach void setUp(){MockitoAnnotations.openMocks(this);service=new MantenerPasoService(procedimientos,pasos);when(procedimientos.buscarPorId(1)).thenReturn(Optional.of(mock(Procedimiento.class)));}
    @Test void creaPasoCritico(){when(pasos.guardar(any())).thenAnswer(i->i.getArgument(0));Paso p=service.crear(1,1,"Apagar",true);assertTrue(p.esCritico());assertEquals(1,p.procedimientoId());}
    @Test void creaPasoNoCritico(){when(pasos.guardar(any())).thenAnswer(i->i.getArgument(0));assertFalse(service.crear(1,1,"Mirar",false).esCritico());}
    @Test void rechazaOrdenDuplicadoControlado(){when(pasos.existeOrden(1,2)).thenReturn(true);assertThrows(OrdenPasoDuplicadoException.class,()->service.crear(1,2,"X",false));verify(pasos,never()).guardar(any());}
    @Test void mismoOrdenEnProcedimientosDistintosEsValido(){when(procedimientos.buscarPorId(2)).thenReturn(Optional.of(mock(Procedimiento.class)));when(pasos.existeOrden(1,1)).thenReturn(true);when(pasos.existeOrden(2,1)).thenReturn(false);when(pasos.guardar(any())).thenAnswer(i->i.getArgument(0));assertDoesNotThrow(()->service.crear(2,1,"X",false));}
    @Test void modificaPasoPropio(){when(pasos.buscarPorId(5)).thenReturn(Optional.of(new Paso(5,1,1,"A",false)));when(pasos.guardar(any())).thenAnswer(i->i.getArgument(0));Paso p=service.modificar(1,5,2,"B",true);assertEquals(2,p.orden());assertTrue(p.esCritico());}
    @Test void noModificaPasoAjeno(){when(pasos.buscarPorId(5)).thenReturn(Optional.of(new Paso(5,2,1,"A",false)));assertThrows(PasoNoEncontradoException.class,()->service.modificar(1,5,2,"B",true));}
    @Test void procedimientoInexistente(){assertThrows(ProcedimientoNoEncontradoException.class,()->service.crear(404,1,"X",false));}
    @Test void listaEnOrdenProvistoPorPuerto(){when(pasos.buscarPorProcedimientoOrdenados(1)).thenReturn(List.of(new Paso(2,1,1,"A",false),new Paso(1,1,2,"B",false)));List<Paso> r=service.listar(1);assertEquals(List.of(1,2),r.stream().map(Paso::orden).toList());verify(pasos).buscarPorProcedimientoOrdenados(1);}
}
