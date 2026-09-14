package cl.casol.backend.identidad.infrastructure.persistence.adapter;

import cl.casol.backend.identidad.application.port.out.ResponsableRepository;
import cl.casol.backend.identidad.domain.Responsable;
import cl.casol.backend.identidad.infrastructure.persistence.mapper.OrganizacionMapper;
import cl.casol.backend.identidad.infrastructure.persistence.repository.ResponsableJpaRepository;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ResponsableRepositoryAdapter implements ResponsableRepository {
    private final ResponsableJpaRepository repository;
    public ResponsableRepositoryAdapter(ResponsableJpaRepository repository) { this.repository = repository; }

    @Override
    public List<Responsable> buscarActivosPorDepartamentoOrdenadosPorNombre(Integer departamentoId) {
        return repository.findByDepartamentoIdAndActivoTrueOrderByNombreAsc(departamentoId).stream()
                .map(OrganizacionMapper::toDomain).toList();
    }
}
