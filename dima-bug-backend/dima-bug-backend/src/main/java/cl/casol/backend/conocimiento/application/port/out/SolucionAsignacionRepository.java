package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.SolucionAsignacion;
import java.util.List;
import java.util.Optional;

public interface SolucionAsignacionRepository {
    List<SolucionAsignacion> buscarPorSolucionOrdenadas(Integer solucionId);
    Optional<SolucionAsignacion> buscarPorId(Integer id);
    SolucionAsignacion guardar(SolucionAsignacion asignacion);
}
