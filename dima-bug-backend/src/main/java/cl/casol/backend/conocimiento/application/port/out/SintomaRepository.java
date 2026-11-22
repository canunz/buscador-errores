package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.Sintoma;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de síntoma.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface SintomaRepository {
    // Busca por conocimiento ordenados.
    List<Sintoma> buscarPorConocimientoOrdenados(Integer conocimientoId);
    // Busca por id.
    Optional<Sintoma> buscarPorId(Integer id);
    // Guarda la síntoma.
    Sintoma guardar(Sintoma sintoma);
}
