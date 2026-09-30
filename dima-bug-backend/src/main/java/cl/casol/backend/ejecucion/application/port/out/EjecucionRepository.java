package cl.casol.backend.ejecucion.application.port.out;

import cl.casol.backend.ejecucion.domain.Ejecucion;
import java.util.List;
import java.util.Optional;

public interface EjecucionRepository {
    Ejecucion guardar(Ejecucion ejecucion);
    Optional<Ejecucion> buscarPorId(Integer id);
    List<Ejecucion> buscarTodas();
    List<Ejecucion> buscarPorUsuario(Integer usuarioId);
}
