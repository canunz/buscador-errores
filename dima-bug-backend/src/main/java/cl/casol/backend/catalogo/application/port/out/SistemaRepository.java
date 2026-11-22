package cl.casol.backend.catalogo.application.port.out;

import cl.casol.backend.catalogo.domain.Hardware;
import cl.casol.backend.catalogo.domain.Sistema;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de sistema.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface SistemaRepository {
    // Busca activos.
    List<Sistema> buscarActivos();
    // Busca activo por id.
    Optional<Sistema> buscarActivoPorId(Integer id);
    // Busca hardware activo por sistema.
    List<Hardware> buscarHardwareActivoPorSistema(Integer sistemaId);
}
