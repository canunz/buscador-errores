package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.MaterialApoyo;
import java.util.List;
import java.util.Optional;

public interface MaterialApoyoRepository {
    List<MaterialApoyo> buscarDirectosPorConocimiento(Integer conocimientoId);
    List<MaterialApoyo> buscarPorPaso(Integer pasoId);
    Optional<MaterialApoyo> buscarPorId(Integer id);
    MaterialApoyo guardar(MaterialApoyo material);
}
