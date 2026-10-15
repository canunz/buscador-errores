package cl.casol.backend.shared.application.service;
import cl.casol.backend.conocimiento.application.port.out.ConocimientoRepository;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.ConocimientoNoEncontradoException;
import cl.casol.backend.procedimiento.application.port.out.ProcedimientoRepository;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.procedimiento.domain.exception.ProcedimientoNoEncontradoException;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class VisibilidadContenidoServiceTest {
    ConocimientoRepository c=mock(ConocimientoRepository.class); ProcedimientoRepository p=mock(ProcedimientoRepository.class);
    VisibilidadContenidoService service=new VisibilidadContenidoService(c,p);
    void conocimiento(EstadoConocimiento estado) {when(c.buscarPorIdIncluidoEliminado(1)).thenReturn(Optional.of(new Conocimiento(1,"T","D",estado,null,null,null,null,null,null,null,null,null)));}
    @Test void usuarioNoVeHijosDeBorrador(){conocimiento(EstadoConocimiento.BORRADOR);assertThrows(ConocimientoNoEncontradoException.class,()->service.verificar("conocimientos",1,false,false,false));}
    @Test void staffVeBorrador(){conocimiento(EstadoConocimiento.BORRADOR);assertDoesNotThrow(()->service.verificar("conocimientos",1,true,false,false));}
    @Test void historicoEliminadoSoloAdministrador(){conocimiento(EstadoConocimiento.ELIMINADO);assertThrows(ConocimientoNoEncontradoException.class,()->service.verificar("conocimientos",1,true,false,true));assertDoesNotThrow(()->service.verificar("conocimientos",1,true,true,true));assertThrows(ConocimientoNoEncontradoException.class,()->service.verificar("conocimientos",1,true,true,false));}
    @Test void usuarioNoVeProcedimientoBorrador(){when(p.buscarPorId(1)).thenReturn(Optional.of(new Procedimiento(1,"T","D",EstadoProcedimiento.BORRADOR,null,null,null,null)));assertThrows(ProcedimientoNoEncontradoException.class,()->service.verificar("procedimientos",1,false,false,false));}
}
