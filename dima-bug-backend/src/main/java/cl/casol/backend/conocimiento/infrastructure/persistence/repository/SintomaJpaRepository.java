package cl.casol.backend.conocimiento.infrastructure.persistence.repository;

import cl.casol.backend.conocimiento.infrastructure.persistence.entity.SintomaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SintomaJpaRepository extends JpaRepository<SintomaEntity, Integer> {
    List<SintomaEntity> findByConocimientoIdOrderByOrdenAsc(Integer conocimientoId);
}
