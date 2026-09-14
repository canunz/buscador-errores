package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.Conocimiento;

import java.util.List;
import java.util.Optional;

public interface ConocimientoRepository {
    List<Conocimiento> buscarTodos();
    Optional<Conocimiento> buscarPorId(Integer id);
    Conocimiento guardar(Conocimiento conocimiento);
}
