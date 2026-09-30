package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.ConocimientoPrueba;
import java.util.List;
import java.util.Optional;

public interface ConocimientoPruebaRepository {
    List<ConocimientoPrueba> buscarActivasPorConocimientoOrdenadas(Integer conocimientoId);
    Optional<ConocimientoPrueba> buscarPorIds(Integer conocimientoId, Integer pruebaId);
    boolean existe(Integer conocimientoId, Integer pruebaId);
    ConocimientoPrueba guardar(ConocimientoPrueba asociacion);
}
