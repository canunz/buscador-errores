package cl.casol.backend.ejecucion.application.port.out;

import cl.casol.backend.ejecucion.domain.EjecucionPaso;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de ejecución paso.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface EjecucionPasoRepository {
    // Guarda el paso.
    EjecucionPaso guardar(EjecucionPaso paso);
    // Guarda todos.
    List<EjecucionPaso> guardarTodos(List<EjecucionPaso> pasos);
    // Busca por id.
    Optional<EjecucionPaso> buscarPorId(Integer id);
    // Busca por ejecución.
    List<EjecucionPaso> buscarPorEjecucion(Integer ejecucionId);
}
