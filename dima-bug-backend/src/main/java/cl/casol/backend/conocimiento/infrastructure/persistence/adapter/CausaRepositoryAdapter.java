package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;

import cl.casol.backend.conocimiento.application.port.out.CausaRepository;
import cl.casol.backend.conocimiento.domain.Causa;
import cl.casol.backend.conocimiento.infrastructure.persistence.mapper.CausaMapper;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.CausaJpaRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class CausaRepositoryAdapter implements CausaRepository {
    private final CausaJpaRepository repository;

    public CausaRepositoryAdapter(CausaJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Causa> buscarPorConocimientoOrdenadas(Integer conocimientoId) {
        return repository.findByConocimientoIdOrderByOrdenAsc(conocimientoId).stream()
                .map(CausaMapper::toDomain).toList();
    }

    @Override
    public Optional<Causa> buscarPorId(Integer id) {
        return repository.findById(id).map(CausaMapper::toDomain);
    }

    @Override
    public Causa guardar(Causa causa) {
        return CausaMapper.toDomain(repository.save(CausaMapper.toEntity(causa)));
    }
}
