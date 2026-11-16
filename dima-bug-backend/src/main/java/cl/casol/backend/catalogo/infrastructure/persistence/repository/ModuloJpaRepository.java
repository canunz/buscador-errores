package cl.casol.backend.catalogo.infrastructure.persistence.repository;

import cl.casol.backend.catalogo.infrastructure.persistence.entity.ModuloEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ModuloJpaRepository extends JpaRepository<ModuloEntity, Integer> {
    List<ModuloEntity> findAllBySistemaIdAndActivoTrueOrderByNombreAsc(Integer sistemaId);
    Optional<ModuloEntity> findByIdAndActivoTrue(Integer id);
}
