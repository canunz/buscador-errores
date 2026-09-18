package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.Sintoma;
import java.util.List;
import java.util.Optional;

public interface SintomaRepository {
    List<Sintoma> buscarPorConocimientoOrdenados(Integer conocimientoId);
    Optional<Sintoma> buscarPorId(Integer id);
    Sintoma guardar(Sintoma sintoma);
}
