package cl.casol.backend.procedimiento.application.port.out;

import cl.casol.backend.procedimiento.domain.Procedimiento;
import java.util.List;
import java.util.Optional;

public interface ProcedimientoRepository {
    List<Procedimiento> buscarTodos();
    Optional<Procedimiento> buscarPorId(Integer id);
    Procedimiento guardar(Procedimiento procedimiento);
}
