package cl.casol.backend.identidad.application.port.out;

import cl.casol.backend.identidad.domain.DepartamentoContacto;
import java.util.List;

/**
 * Repositorio Spring Data de departamento contacto.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface DepartamentoContactoRepository {
    // Busca activos por departamento ordenados por id.
    List<DepartamentoContacto> buscarActivosPorDepartamentoOrdenadosPorId(Integer departamentoId);
}
