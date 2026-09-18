package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;

import cl.casol.backend.conocimiento.application.port.out.SintomaRepository;
import cl.casol.backend.conocimiento.domain.Sintoma;
import cl.casol.backend.conocimiento.infrastructure.persistence.mapper.SintomaMapper;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.SintomaJpaRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class SintomaRepositoryAdapter implements SintomaRepository {
    private final SintomaJpaRepository repository;

    public SintomaRepositoryAdapter(SintomaJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Sintoma> buscarPorConocimientoOrdenados(Integer conocimientoId) {
        return repository.findByConocimientoIdOrderByOrdenAsc(conocimientoId).stream()
                .map(SintomaMapper::toDomain).toList();
    }

    @Override
    public Optional<Sintoma> buscarPorId(Integer id) {
        return repository.findById(id).map(SintomaMapper::toDomain);
    }

    @Override
    public Sintoma guardar(Sintoma sintoma) {
        return SintomaMapper.toDomain(repository.save(SintomaMapper.toEntity(sintoma)));
    }
}
