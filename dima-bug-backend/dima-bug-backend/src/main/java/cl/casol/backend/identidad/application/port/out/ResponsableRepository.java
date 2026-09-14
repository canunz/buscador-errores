package cl.casol.backend.identidad.application.port.out;

import cl.casol.backend.identidad.domain.Responsable;
import java.util.List;

public interface ResponsableRepository {
    List<Responsable> buscarActivosPorDepartamentoOrdenadosPorNombre(Integer departamentoId);
}
