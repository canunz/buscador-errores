package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.ConocimientoPrueba;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de conocimiento prueba.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface ConocimientoPruebaRepository {
    // Busca por conocimiento ordenadas.
    List<ConocimientoPrueba> buscarPorConocimientoOrdenadas(Integer conocimientoId);
    // Busca activas por conocimiento ordenadas.
    List<ConocimientoPrueba> buscarActivasPorConocimientoOrdenadas(Integer conocimientoId);
    // Busca por ids.
    Optional<ConocimientoPrueba> buscarPorIds(Integer conocimientoId, Integer pruebaId);
    // Indica si existe.
    boolean existe(Integer conocimientoId, Integer pruebaId);
    // Guarda la asociación.
    ConocimientoPrueba guardar(ConocimientoPrueba asociacion);
}
