package cl.casol.backend.catalogo.infrastructure.persistence.repository;

import cl.casol.backend.catalogo.infrastructure.persistence.entity.HardwareEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface HardwareJpaRepository extends JpaRepository<HardwareEntity, Integer> {
    List<HardwareEntity> findAllByActivoTrueOrderByNombreAsc();

    @EntityGraph(attributePaths = "sistemas")
    Optional<HardwareEntity> findWithSistemasByIdAndActivoTrue(Integer id);

    Optional<HardwareEntity> findByIdAndActivoTrue(Integer id);
}
