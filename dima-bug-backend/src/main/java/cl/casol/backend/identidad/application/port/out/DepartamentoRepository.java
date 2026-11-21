package cl.casol.backend.identidad.application.port.out;

import cl.casol.backend.identidad.domain.Departamento;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de departamento.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface DepartamentoRepository {
    // Busca activos ordenados por nombre.
    List<Departamento> buscarActivosOrdenadosPorNombre();
    // Busca activo por id.
    Optional<Departamento> buscarActivoPorId(Integer id);
}
