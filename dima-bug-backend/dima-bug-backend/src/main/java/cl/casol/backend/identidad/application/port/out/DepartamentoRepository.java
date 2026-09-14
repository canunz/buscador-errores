package cl.casol.backend.identidad.application.port.out;

import cl.casol.backend.identidad.domain.Departamento;
import java.util.List;
import java.util.Optional;

public interface DepartamentoRepository {
    List<Departamento> buscarActivosOrdenadosPorNombre();
    Optional<Departamento> buscarActivoPorId(Integer id);
}
