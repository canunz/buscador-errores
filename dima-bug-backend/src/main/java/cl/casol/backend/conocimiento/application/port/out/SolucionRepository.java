package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.Solucion;
import java.util.List;
import java.util.Optional;

public interface SolucionRepository {
    List<Solucion> buscarPorConocimientoOrdenadas(Integer conocimientoId);
    Optional<Solucion> buscarPorId(Integer id);
    Solucion guardar(Solucion solucion);
}
