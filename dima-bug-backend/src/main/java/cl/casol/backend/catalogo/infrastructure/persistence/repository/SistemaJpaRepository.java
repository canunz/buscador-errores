package cl.casol.backend.catalogo.infrastructure.persistence.repository;

import cl.casol.backend.catalogo.infrastructure.persistence.entity.SistemaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de sistema.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface SistemaJpaRepository extends JpaRepository<SistemaEntity, Integer> {
    // Busca todos los registros por activo en true, ordenados por nombre de menor a mayor.
    List<SistemaEntity> findAllByActivoTrueOrderByNombreAsc();

    // Busca con hardware por id y activo en true.
    @EntityGraph(attributePaths = "hardware")
    Optional<SistemaEntity> findWithHardwareByIdAndActivoTrue(Integer id);

    // Busca registros por id y activo en true.
    Optional<SistemaEntity> findByIdAndActivoTrue(Integer id);
}
