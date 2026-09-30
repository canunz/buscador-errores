package cl.casol.backend.conocimiento.infrastructure.persistence.repository;

import cl.casol.backend.conocimiento.infrastructure.persistence.entity.SolucionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SolucionJpaRepository extends JpaRepository<SolucionEntity, Integer> {
    List<SolucionEntity> findByConocimientoIdOrderByOrdenAsc(Integer conocimientoId);
}
