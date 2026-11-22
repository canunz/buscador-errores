package cl.casol.backend.identidad.infrastructure.persistence.adapter;

import cl.casol.backend.identidad.application.port.out.DepartamentoContactoRepository;
import cl.casol.backend.identidad.domain.DepartamentoContacto;
import cl.casol.backend.identidad.infrastructure.persistence.mapper.OrganizacionMapper;
import cl.casol.backend.identidad.infrastructure.persistence.repository.DepartamentoContactoJpaRepository;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Adaptador de base de datos para departamento contacto.
 * Implementa el puerto de salida usando JPA y convierte entre entidades y objetos del dominio.
 */
@Component
public class DepartamentoContactoRepositoryAdapter implements DepartamentoContactoRepository {
    private final DepartamentoContactoJpaRepository repository;

    public DepartamentoContactoRepositoryAdapter(DepartamentoContactoJpaRepository repository) {
        this.repository = repository;
    }

    // Busca activos por departamento ordenados por id.
    @Override
    public List<DepartamentoContacto> buscarActivosPorDepartamentoOrdenadosPorId(Integer departamentoId) {
        return repository.findByDepartamentoIdAndActivoTrueOrderByIdAsc(departamentoId).stream()
                .map(OrganizacionMapper::toDomain)
                .toList();
    }
}
