package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.Solucion;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de solución.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface SolucionRepository {
    // Busca publicas.
    List<Solucion> buscarPublicas(String texto,int limite);
    // Busca activas autoservicio por conocimiento.
    List<Solucion> buscarActivasAutoservicioPorConocimiento(Integer conocimientoId);
    // Busca por conocimiento ordenadas.
    List<Solucion> buscarPorConocimientoOrdenadas(Integer conocimientoId);
    // Busca por id.
    Optional<Solucion> buscarPorId(Integer id);
    // Busca activas por conocimiento.
    List<Solucion> buscarActivasPorConocimiento(Integer conocimientoId);
    // Bloquea por conocimiento.
    List<Solucion> bloquearPorConocimiento(Integer conocimientoId);
    // Guarda la solución.
    Solucion guardar(Solucion solucion);
}
