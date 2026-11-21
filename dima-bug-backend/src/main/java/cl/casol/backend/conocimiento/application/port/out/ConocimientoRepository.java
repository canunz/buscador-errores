package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.Conocimiento;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de conocimiento.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface ConocimientoRepository {
    // Busca todos.
    List<Conocimiento> buscarTodos();
    // Busca por id.
    Optional<Conocimiento> buscarPorId(Integer id);
    // Busca por id incluido eliminado.
    Optional<Conocimiento> buscarPorIdIncluidoEliminado(Integer id);
    // Bloquea por id.
    void bloquear(Integer id);
    // Guarda el conocimiento.
    Conocimiento guardar(Conocimiento conocimiento);
}
