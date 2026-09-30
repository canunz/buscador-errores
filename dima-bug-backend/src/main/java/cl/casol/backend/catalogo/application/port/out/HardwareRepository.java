package cl.casol.backend.catalogo.application.port.out;

import cl.casol.backend.catalogo.domain.Hardware;
import cl.casol.backend.catalogo.domain.Sistema;
import java.util.List;
import java.util.Optional;

public interface HardwareRepository {
    List<Hardware> buscarActivos();
    Optional<Hardware> buscarActivoPorId(Integer id);
    List<Sistema> buscarSistemasActivosPorHardware(Integer hardwareId);
}
