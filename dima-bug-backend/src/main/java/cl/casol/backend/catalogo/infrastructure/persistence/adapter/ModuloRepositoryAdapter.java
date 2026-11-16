package cl.casol.backend.catalogo.infrastructure.persistence.adapter;

import cl.casol.backend.catalogo.application.port.out.ModuloRepository;
import cl.casol.backend.catalogo.domain.Modulo;
import cl.casol.backend.catalogo.infrastructure.persistence.mapper.CatalogoMapper;
import cl.casol.backend.catalogo.infrastructure.persistence.repository.ModuloJpaRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class ModuloRepositoryAdapter implements ModuloRepository {
    private final ModuloJpaRepository repository;

    public ModuloRepositoryAdapter(ModuloJpaRepository repository) { this.repository = repository; }

    @Override
    public List<Modulo> buscarActivosPorSistema(Integer sistemaId) {
        return repository.findAllBySistemaIdAndActivoTrueOrderByNombreAsc(sistemaId).stream()
                .map(CatalogoMapper::toDomain).toList();
    }

    @Override
    public Optional<Modulo> buscarActivoPorId(Integer id) {
        return repository.findByIdAndActivoTrue(id).map(CatalogoMapper::toDomain);
    }
}
