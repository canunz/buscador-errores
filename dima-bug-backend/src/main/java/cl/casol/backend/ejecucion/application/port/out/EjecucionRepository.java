package cl.casol.backend.ejecucion.application.port.out;

import cl.casol.backend.ejecucion.domain.Ejecucion;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de ejecución.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface EjecucionRepository {
    // Guarda la ejecución.
    Ejecucion guardar(Ejecucion ejecucion);
    // Busca por id.
    Optional<Ejecucion> buscarPorId(Integer id);
    /** Serializa todos los escritores del agregado; requiere transacción activa. */
    void bloquear(Integer id);
    // Busca todas.
    List<Ejecucion> buscarTodas();
    // Busca por usuario.
    List<Ejecucion> buscarPorUsuario(Integer usuarioId);
}
