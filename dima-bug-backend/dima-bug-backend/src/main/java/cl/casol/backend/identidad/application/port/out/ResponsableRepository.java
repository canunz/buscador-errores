package cl.casol.backend.identidad.application.port.out;

import cl.casol.backend.identidad.domain.Responsable;
import java.util.List;
import java.util.Optional;

public interface ResponsableRepository {
    List<Responsable> buscarActivosPorDepartamentoOrdenadosPorNombre(Integer departamentoId);
    Optional<Responsable> buscarActivoPorId(Integer id);
}
