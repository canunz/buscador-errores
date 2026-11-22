package cl.casol.backend.procedimiento.application.port.out;

import cl.casol.backend.procedimiento.domain.Procedimiento;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de procedimiento.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface ProcedimientoRepository {
    // Indica si tiene ejecuciones.
    default boolean tieneEjecuciones(Integer id){return false;}
    // Bloquea por id.
    void bloquear(Integer id);
    // Busca todos.
    List<Procedimiento> buscarTodos();
    // Busca por id.
    Optional<Procedimiento> buscarPorId(Integer id);
    // Guarda el procedimiento.
    Procedimiento guardar(Procedimiento procedimiento);
}
