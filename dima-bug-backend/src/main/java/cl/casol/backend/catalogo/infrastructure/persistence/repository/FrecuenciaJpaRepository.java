package cl.casol.backend.catalogo.infrastructure.persistence.repository;

import cl.casol.backend.catalogo.infrastructure.persistence.entity.FrecuenciaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface FrecuenciaJpaRepository extends JpaRepository<FrecuenciaEntity, Integer> {
    Optional<FrecuenciaEntity> findByIdAndActivaTrue(Integer id);
    List<FrecuenciaEntity> findByActivaTrueOrderByOrdenAsc();
}
