package cl.casol.backend.identidad.application.port.out;

import cl.casol.backend.identidad.domain.DepartamentoContacto;
import java.util.List;

public interface DepartamentoContactoRepository {
    List<DepartamentoContacto> buscarActivosPorDepartamentoOrdenadosPorId(Integer departamentoId);
}
