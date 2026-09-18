package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;

import cl.casol.backend.conocimiento.application.port.out.MaterialApoyoRepository;
import cl.casol.backend.conocimiento.domain.MaterialApoyo;
import cl.casol.backend.conocimiento.infrastructure.persistence.mapper.MaterialApoyoMapper;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.MaterialApoyoJpaRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class MaterialApoyoRepositoryAdapter implements MaterialApoyoRepository {
    private final MaterialApoyoJpaRepository repository;

    public MaterialApoyoRepositoryAdapter(MaterialApoyoJpaRepository repository) { this.repository = repository; }

    @Override
    public List<MaterialApoyo> buscarDirectosPorConocimiento(Integer conocimientoId) {
        return repository.findByConocimientoIdAndPasoIdIsNullOrderByIdAsc(conocimientoId).stream()
                .map(MaterialApoyoMapper::toDomain).toList();
    }

    @Override
    public Optional<MaterialApoyo> buscarPorId(Integer id) {
        return repository.findById(id).map(MaterialApoyoMapper::toDomain);
    }

    @Override
    public MaterialApoyo guardar(MaterialApoyo material) {
        return MaterialApoyoMapper.toDomain(repository.save(MaterialApoyoMapper.toEntity(material)));
    }
}
