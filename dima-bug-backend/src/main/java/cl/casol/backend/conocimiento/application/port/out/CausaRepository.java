package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.Causa;
import java.util.List;
import java.util.Optional;

public interface CausaRepository {
    List<Causa> buscarPorConocimientoOrdenadas(Integer conocimientoId);
    Optional<Causa> buscarPorId(Integer id);
    Causa guardar(Causa causa);
}
