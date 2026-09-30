package cl.casol.backend.procedimiento.application.port.out;

import cl.casol.backend.procedimiento.domain.Paso;
import java.util.List;
import java.util.Optional;

public interface PasoRepository {
    List<Paso> buscarPorProcedimientoOrdenados(Integer procedimientoId);
    Optional<Paso> buscarPorId(Integer id);
    boolean existeOrden(Integer procedimientoId, Integer orden);
    boolean existeOrdenExcluyendoPaso(Integer procedimientoId, Integer orden, Integer pasoId);
    Paso guardar(Paso paso);
}
