package cl.casol.backend.identidad.infrastructure.persistence.adapter;

import cl.casol.backend.identidad.application.port.out.DepartamentoRepository;
import cl.casol.backend.identidad.domain.Departamento;
import cl.casol.backend.identidad.infrastructure.persistence.mapper.OrganizacionMapper;
import cl.casol.backend.identidad.infrastructure.persistence.repository.DepartamentoJpaRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class DepartamentoRepositoryAdapter implements DepartamentoRepository {
    private final DepartamentoJpaRepository repository;
    public DepartamentoRepositoryAdapter(DepartamentoJpaRepository repository) { this.repository = repository; }

    @Override
    public List<Departamento> buscarActivosOrdenadosPorNombre() {
        return repository.findByActivoTrueOrderByNombreAsc().stream().map(OrganizacionMapper::toDomain).toList();
    }

    @Override
    public Optional<Departamento> buscarActivoPorId(Integer id) {
        return repository.findByIdAndActivoTrue(id).map(OrganizacionMapper::toDomain);
    }
}
