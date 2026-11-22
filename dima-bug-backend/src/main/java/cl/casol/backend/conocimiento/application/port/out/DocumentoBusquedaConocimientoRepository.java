package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.DocumentoBusquedaConocimiento;
import java.util.Optional;

/**
 * Repositorio Spring Data de documento búsqueda conocimiento.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface DocumentoBusquedaConocimientoRepository {
    // Obtiene el conocimiento indicado.
    Optional<DocumentoBusquedaConocimiento> obtener(Integer conocimientoId);
    // Guarda el conocimiento indicado.
    void guardar(Integer conocimientoId, String contenido);
    // Elimina el conocimiento indicado.
    void eliminar(Integer conocimientoId);
}
