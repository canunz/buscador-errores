package cl.casol.backend.catalogo.application.port.out;

import cl.casol.backend.catalogo.domain.Hardware;
import cl.casol.backend.catalogo.domain.Sistema;
import java.util.List;
import java.util.Optional;

public interface SistemaRepository {
    List<Sistema> buscarActivos();
    Optional<Sistema> buscarActivoPorId(Integer id);
    List<Hardware> buscarHardwareActivoPorSistema(Integer sistemaId);
}
