package cl.casol.backend.shared.application.service;
import cl.casol.backend.conocimiento.application.port.out.ConocimientoRepository;
import cl.casol.backend.conocimiento.domain.EstadoConocimiento;
import cl.casol.backend.conocimiento.domain.exception.ConocimientoNoEncontradoException;
import cl.casol.backend.procedimiento.application.port.out.ProcedimientoRepository;
import cl.casol.backend.procedimiento.domain.EstadoProcedimiento;
import cl.casol.backend.procedimiento.domain.exception.ProcedimientoNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class VisibilidadContenidoService {
    private final ConocimientoRepository conocimientos;
    private final ProcedimientoRepository procedimientos;
    public VisibilidadContenidoService(ConocimientoRepository conocimientos,ProcedimientoRepository procedimientos) {
        this.conocimientos=conocimientos; this.procedimientos=procedimientos;
    }
    public void verificar(String tipo,Integer id,boolean staff,boolean administrador,boolean historico) {
        if ("conocimientos".equals(tipo)) {
            var c=conocimientos.buscarPorIdIncluidoEliminado(id).orElseThrow(() -> new ConocimientoNoEncontradoException(id));
            if (c.getEstado()==EstadoConocimiento.ELIMINADO) {
                if (!(administrador && historico)) throw new ConocimientoNoEncontradoException(id);
            } else if (!staff && c.getEstado()!=EstadoConocimiento.PUBLICADO) throw new ConocimientoNoEncontradoException(id);
        } else {
            var p=procedimientos.buscarPorId(id).orElseThrow(() -> new ProcedimientoNoEncontradoException(id));
            if (!staff && p.estado()!=EstadoProcedimiento.PUBLICADO) throw new ProcedimientoNoEncontradoException(id);
        }
    }
}
