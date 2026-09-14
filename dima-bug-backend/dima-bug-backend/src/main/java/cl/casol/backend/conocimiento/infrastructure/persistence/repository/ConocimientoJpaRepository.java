package cl.casol.backend.conocimiento.infrastructure.persistence.repository;

import cl.casol.backend.conocimiento.infrastructure.persistence.entity.ConocimientoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConocimientoJpaRepository extends JpaRepository<ConocimientoEntity, Integer> {
    List<ConocimientoEntity> findAllByOrderByFechaCreacionDesc();
}
