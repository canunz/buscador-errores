package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.DocumentoBusquedaConocimiento;
import java.util.Optional;

public interface DocumentoBusquedaConocimientoRepository {
    Optional<DocumentoBusquedaConocimiento> obtener(Integer conocimientoId);
    void guardar(Integer conocimientoId, String contenido);
    void eliminar(Integer conocimientoId);
}
