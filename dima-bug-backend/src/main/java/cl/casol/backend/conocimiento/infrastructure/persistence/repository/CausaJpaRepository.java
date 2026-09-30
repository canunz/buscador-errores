package cl.casol.backend.conocimiento.infrastructure.persistence.repository;

import cl.casol.backend.conocimiento.infrastructure.persistence.entity.CausaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CausaJpaRepository extends JpaRepository<CausaEntity, Integer> {
    List<CausaEntity> findByConocimientoIdOrderByOrdenAsc(Integer conocimientoId);
}
