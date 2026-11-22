package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;

import cl.casol.backend.conocimiento.application.port.out.CausaRepository;
import cl.casol.backend.conocimiento.domain.Causa;
import cl.casol.backend.conocimiento.infrastructure.persistence.mapper.CausaMapper;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.CausaJpaRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador de base de datos para causa.
 * Implementa el puerto de salida usando JPA y convierte entre entidades y objetos del dominio.
 */
@Component
public class CausaRepositoryAdapter implements CausaRepository {
    private final CausaJpaRepository repository;

    public CausaRepositoryAdapter(CausaJpaRepository repository) {
        this.repository = repository;
    }

    // Busca por conocimiento ordenadas.
    @Override
    public List<Causa> buscarPorConocimientoOrdenadas(Integer conocimientoId) {
        return repository.findByConocimientoIdOrderByOrdenAsc(conocimientoId).stream()
                .map(CausaMapper::toDomain).toList();
    }

    // Busca por id.
    @Override
    public Optional<Causa> buscarPorId(Integer id) {
        return repository.findById(id).map(CausaMapper::toDomain);
    }

    // Guarda la causa.
    @Override
    public Causa guardar(Causa causa) {
        return CausaMapper.toDomain(repository.save(CausaMapper.toEntity(causa)));
    }
}
