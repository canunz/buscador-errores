package cl.casol.backend.conocimiento.infrastructure.persistence.repository;

import cl.casol.backend.conocimiento.infrastructure.persistence.entity.MaterialApoyoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MaterialApoyoJpaRepository extends JpaRepository<MaterialApoyoEntity, Integer> {
    List<MaterialApoyoEntity> findByConocimientoIdAndPasoIdIsNullOrderByIdAsc(Integer conocimientoId);
}
