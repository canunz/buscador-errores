package cl.casol.backend.catalogo.infrastructure.persistence.adapter;

import cl.casol.backend.catalogo.application.port.out.SistemaRepository;
import cl.casol.backend.catalogo.domain.Hardware;
import cl.casol.backend.catalogo.domain.Sistema;
import cl.casol.backend.catalogo.infrastructure.persistence.mapper.CatalogoMapper;
import cl.casol.backend.catalogo.infrastructure.persistence.repository.SistemaJpaRepository;
import org.springframework.stereotype.Component;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador de base de datos para sistema.
 * Implementa el puerto de salida usando JPA y convierte entre entidades y objetos del dominio.
 */
@Component
public class SistemaRepositoryAdapter implements SistemaRepository {
    private final SistemaJpaRepository repository;

    public SistemaRepositoryAdapter(SistemaJpaRepository repository) { this.repository = repository; }

    // Busca activos.
    @Override
    public List<Sistema> buscarActivos() {
        return repository.findAllByActivoTrueOrderByNombreAsc().stream().map(CatalogoMapper::toDomain).toList();
    }

    // Busca activo por id.
    @Override
    public Optional<Sistema> buscarActivoPorId(Integer id) {
        return repository.findByIdAndActivoTrue(id).map(CatalogoMapper::toDomain);
    }

    // Busca hardware activo por sistema.
    @Override
    public List<Hardware> buscarHardwareActivoPorSistema(Integer sistemaId) {
        return repository.findWithHardwareByIdAndActivoTrue(sistemaId).stream()
                .flatMap(entity -> entity.getHardware().stream())
                .filter(entity -> entity.isActivo())
                .map(CatalogoMapper::toDomain)
                .sorted(Comparator.comparing(Hardware::nombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
