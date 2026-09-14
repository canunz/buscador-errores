package cl.casol.backend.catalogo.infrastructure.persistence.adapter;

import cl.casol.backend.catalogo.application.port.out.HardwareRepository;
import cl.casol.backend.catalogo.domain.Hardware;
import cl.casol.backend.catalogo.domain.Sistema;
import cl.casol.backend.catalogo.infrastructure.persistence.mapper.CatalogoMapper;
import cl.casol.backend.catalogo.infrastructure.persistence.repository.HardwareJpaRepository;
import org.springframework.stereotype.Component;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class HardwareRepositoryAdapter implements HardwareRepository {
    private final HardwareJpaRepository repository;

    public HardwareRepositoryAdapter(HardwareJpaRepository repository) { this.repository = repository; }

    @Override
    public List<Hardware> buscarActivos() {
        return repository.findAllByActivoTrueOrderByNombreAsc().stream().map(CatalogoMapper::toDomain).toList();
    }

    @Override
    public Optional<Hardware> buscarActivoPorId(Integer id) {
        return repository.findByIdAndActivoTrue(id).map(CatalogoMapper::toDomain);
    }

    @Override
    public List<Sistema> buscarSistemasActivosPorHardware(Integer hardwareId) {
        return repository.findWithSistemasByIdAndActivoTrue(hardwareId).stream()
                .flatMap(entity -> entity.getSistemas().stream())
                .filter(entity -> entity.isActivo())
                .map(CatalogoMapper::toDomain)
                .sorted(Comparator.comparing(Sistema::nombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
