package cl.casol.backend.identidad.application.port.out;

import cl.casol.backend.identidad.domain.Rol;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de rol.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface RolRepository {

    // Busca por id.
    Optional<Rol> buscarPorId(Integer id);

    // Busca activos.
    List<Rol> buscarActivos();
}
