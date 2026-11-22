package cl.casol.backend.catalogo.infrastructure.persistence.repository;

import cl.casol.backend.catalogo.infrastructure.persistence.entity.ModuloEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de módulo.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface ModuloJpaRepository extends JpaRepository<ModuloEntity, Integer> {
    // Busca todos los registros por sistema id y activo en true, ordenados por nombre de menor a mayor.
    List<ModuloEntity> findAllBySistemaIdAndActivoTrueOrderByNombreAsc(Integer sistemaId);
    // Busca registros por id y activo en true.
    Optional<ModuloEntity> findByIdAndActivoTrue(Integer id);
}
