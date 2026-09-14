package cl.casol.backend.identidad.application.port.out;

import cl.casol.backend.identidad.domain.Rol;

import java.util.List;
import java.util.Optional;

public interface RolRepository {

    Optional<Rol> buscarPorId(Integer id);

    List<Rol> buscarActivos();
}
