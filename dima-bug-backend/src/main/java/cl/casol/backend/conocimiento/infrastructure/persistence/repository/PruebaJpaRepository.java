package cl.casol.backend.conocimiento.infrastructure.persistence.repository;

import cl.casol.backend.conocimiento.infrastructure.persistence.entity.PruebaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PruebaJpaRepository extends JpaRepository<PruebaEntity, Integer> {
    List<PruebaEntity> findByActivaTrueOrderByDescripcionAsc();
    Optional<PruebaEntity> findByIdAndActivaTrue(Integer id);
}
