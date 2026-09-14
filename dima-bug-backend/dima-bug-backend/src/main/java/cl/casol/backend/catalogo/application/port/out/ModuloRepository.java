package cl.casol.backend.catalogo.application.port.out;

import cl.casol.backend.catalogo.domain.Modulo;
import java.util.List;
import java.util.Optional;

public interface ModuloRepository {
    List<Modulo> buscarActivosPorSistema(Integer sistemaId);
    Optional<Modulo> buscarActivoPorId(Integer id);
}
