package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.Solucion;
import java.util.List;
import java.util.Optional;

public interface SolucionRepository {
    List<Solucion> buscarPorConocimientoOrdenadas(Integer conocimientoId);
    Optional<Solucion> buscarPorId(Integer id);
    List<Solucion> buscarActivasPorConocimiento(Integer conocimientoId);
    List<Solucion> bloquearPorConocimiento(Integer conocimientoId);
    Solucion guardar(Solucion solucion);
}
