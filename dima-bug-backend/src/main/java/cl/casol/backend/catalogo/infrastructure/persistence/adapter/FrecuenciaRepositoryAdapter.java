package cl.casol.backend.catalogo.infrastructure.persistence.adapter;

import cl.casol.backend.catalogo.application.port.out.FrecuenciaRepository;
import cl.casol.backend.catalogo.domain.Frecuencia;
import cl.casol.backend.catalogo.infrastructure.persistence.repository.FrecuenciaJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.List;

@Component
public class FrecuenciaRepositoryAdapter implements FrecuenciaRepository {
    private final FrecuenciaJpaRepository repository;

    public FrecuenciaRepositoryAdapter(FrecuenciaJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Frecuencia> buscarActivaPorId(Integer id) {
        return repository.findByIdAndActivaTrue(id)
                .map(entity -> new Frecuencia(entity.getId(), entity.getNombre(), entity.isActiva()));
    }

    @Override
    public List<Frecuencia> buscarActivasOrdenadas() {
        return repository.findByActivaTrueOrderByOrdenAsc().stream()
                .map(entity -> new Frecuencia(entity.getId(), entity.getNombre(), entity.isActiva()))
                .toList();
    }
}
