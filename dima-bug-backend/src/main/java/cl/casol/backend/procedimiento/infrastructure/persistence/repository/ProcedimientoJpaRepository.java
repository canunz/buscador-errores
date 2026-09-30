package cl.casol.backend.procedimiento.infrastructure.persistence.repository;

import cl.casol.backend.procedimiento.infrastructure.persistence.entity.ProcedimientoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProcedimientoJpaRepository extends JpaRepository<ProcedimientoEntity, Integer> {
    List<ProcedimientoEntity> findAllByOrderByFechaCreacionDesc();
}
