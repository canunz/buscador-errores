package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;

import cl.casol.backend.conocimiento.application.port.out.PruebaRepository;
import cl.casol.backend.conocimiento.domain.Prueba;
import cl.casol.backend.conocimiento.infrastructure.persistence.mapper.PruebaMapper;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.PruebaJpaRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class PruebaRepositoryAdapter implements PruebaRepository {
    private final PruebaJpaRepository repository;

    public PruebaRepositoryAdapter(PruebaJpaRepository repository) { this.repository = repository; }

    @Override
    public List<Prueba> buscarActivasOrdenadasPorDescripcion() {
        return repository.findByActivaTrueOrderByDescripcionAsc().stream().map(PruebaMapper::toDomain).toList();
    }

    @Override
    public Optional<Prueba> buscarActivaPorId(Integer id) {
        return repository.findByIdAndActivaTrue(id).map(PruebaMapper::toDomain);
    }
}
