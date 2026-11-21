package cl.casol.backend.identidad.infrastructure.persistence.adapter;

import cl.casol.backend.identidad.application.port.out.ResponsableRepository;
import cl.casol.backend.identidad.domain.Responsable;
import cl.casol.backend.identidad.infrastructure.persistence.mapper.OrganizacionMapper;
import cl.casol.backend.identidad.infrastructure.persistence.repository.ResponsableJpaRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador de base de datos para responsable.
 * Implementa el puerto de salida usando JPA y convierte entre entidades y objetos del dominio.
 */
@Component
public class ResponsableRepositoryAdapter implements ResponsableRepository {
    private final ResponsableJpaRepository repository;
    public ResponsableRepositoryAdapter(ResponsableJpaRepository repository) { this.repository = repository; }

    // Busca activos por departamento ordenados por nombre.
    @Override
    public List<Responsable> buscarActivosPorDepartamentoOrdenadosPorNombre(Integer departamentoId) {
        return repository.findByDepartamentoIdAndActivoTrueOrderByNombreAsc(departamentoId).stream()
                .map(OrganizacionMapper::toDomain).toList();
    }

    // Busca activo por id.
    @Override
    public Optional<Responsable> buscarActivoPorId(Integer id) {
        return repository.findByIdAndActivoTrue(id).map(OrganizacionMapper::toDomain);
    }
}
