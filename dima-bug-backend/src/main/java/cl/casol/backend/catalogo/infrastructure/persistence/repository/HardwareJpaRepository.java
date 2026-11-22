package cl.casol.backend.catalogo.infrastructure.persistence.repository;

import cl.casol.backend.catalogo.infrastructure.persistence.entity.HardwareEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de hardware.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface HardwareJpaRepository extends JpaRepository<HardwareEntity, Integer> {
    // Busca todos los registros por activo en true, ordenados por nombre de menor a mayor.
    List<HardwareEntity> findAllByActivoTrueOrderByNombreAsc();

    // Busca con sistemas por id y activo en true.
    @EntityGraph(attributePaths = "sistemas")
    Optional<HardwareEntity> findWithSistemasByIdAndActivoTrue(Integer id);

    // Busca registros por id y activo en true.
    Optional<HardwareEntity> findByIdAndActivoTrue(Integer id);
}
