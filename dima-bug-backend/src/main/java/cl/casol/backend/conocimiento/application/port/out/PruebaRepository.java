package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.Prueba;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de prueba.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface PruebaRepository {
    // Bloquea por id.
    void bloquear(Integer id);
    // Busca activas ordenadas por descripción.
    List<Prueba> buscarActivasOrdenadasPorDescripcion();
    // Busca activa por id.
    Optional<Prueba> buscarActivaPorId(Integer id);
}
