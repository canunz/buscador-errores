package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.SolucionAsignacion;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de solución asignación.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface SolucionAsignacionRepository {
    // Busca por solución ordenadas.
    List<SolucionAsignacion> buscarPorSolucionOrdenadas(Integer solucionId);
    // Busca por id.
    Optional<SolucionAsignacion> buscarPorId(Integer id);
    // Guarda la asignación.
    SolucionAsignacion guardar(SolucionAsignacion asignacion);
}
