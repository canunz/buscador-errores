package cl.casol.backend.catalogo.infrastructure.persistence.repository;

import cl.casol.backend.catalogo.infrastructure.persistence.entity.SistemaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SistemaJpaRepository extends JpaRepository<SistemaEntity, Integer> {
    List<SistemaEntity> findAllByActivoTrueOrderByNombreAsc();

    @EntityGraph(attributePaths = "hardware")
    Optional<SistemaEntity> findWithHardwareByIdAndActivoTrue(Integer id);

    Optional<SistemaEntity> findByIdAndActivoTrue(Integer id);
}
