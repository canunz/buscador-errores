package cl.casol.backend.ejecucion.application.port.out;

import cl.casol.backend.ejecucion.domain.EjecucionPaso;
import java.util.List;
import java.util.Optional;

public interface EjecucionPasoRepository {
    EjecucionPaso guardar(EjecucionPaso paso);
    List<EjecucionPaso> guardarTodos(List<EjecucionPaso> pasos);
    Optional<EjecucionPaso> buscarPorId(Integer id);
    List<EjecucionPaso> buscarPorEjecucion(Integer ejecucionId);
}
