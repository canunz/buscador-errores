package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.Prueba;
import java.util.List;
import java.util.Optional;

public interface PruebaRepository {
    void bloquear(Integer id);
    List<Prueba> buscarActivasOrdenadasPorDescripcion();
    Optional<Prueba> buscarActivaPorId(Integer id);
}
