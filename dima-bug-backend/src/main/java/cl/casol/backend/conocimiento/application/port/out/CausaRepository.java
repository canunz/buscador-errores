package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.Causa;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de causa.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface CausaRepository {
    // Busca por conocimiento ordenadas.
    List<Causa> buscarPorConocimientoOrdenadas(Integer conocimientoId);
    // Busca por id.
    Optional<Causa> buscarPorId(Integer id);
    // Guarda la causa.
    Causa guardar(Causa causa);
}
