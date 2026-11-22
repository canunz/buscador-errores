package cl.casol.backend.catalogo.application.port.out;

import cl.casol.backend.catalogo.domain.Hardware;
import cl.casol.backend.catalogo.domain.Sistema;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de hardware.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface HardwareRepository {
    // Busca activos.
    List<Hardware> buscarActivos();
    // Busca por id.
    default Optional<Hardware> buscarPorId(Integer id){return buscarActivoPorId(id);}
    // Busca activo por id.
    Optional<Hardware> buscarActivoPorId(Integer id);
    // Busca sistemas activos por hardware.
    List<Sistema> buscarSistemasActivosPorHardware(Integer hardwareId);
}
