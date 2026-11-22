package cl.casol.backend.catalogo.application.port.out;

import cl.casol.backend.catalogo.domain.Modulo;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de módulo.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface ModuloRepository {
    // Busca activos por sistema.
    List<Modulo> buscarActivosPorSistema(Integer sistemaId);
    // Busca activo por id.
    Optional<Modulo> buscarActivoPorId(Integer id);
}
