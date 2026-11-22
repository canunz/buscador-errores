package cl.casol.backend.identidad.application.port.out;

import cl.casol.backend.identidad.domain.Responsable;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de responsable.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface ResponsableRepository {
    // Busca activos por departamento ordenados por nombre.
    List<Responsable> buscarActivosPorDepartamentoOrdenadosPorNombre(Integer departamentoId);
    // Busca activo por id.
    Optional<Responsable> buscarActivoPorId(Integer id);
}
