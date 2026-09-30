package cl.casol.backend.procedimiento.infrastructure.persistence.repository;

import cl.casol.backend.procedimiento.infrastructure.persistence.entity.PasoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PasoJpaRepository extends JpaRepository<PasoEntity, Integer> {
    List<PasoEntity> findByProcedimientoIdOrderByOrdenAsc(Integer procedimientoId);
    boolean existsByProcedimientoIdAndOrden(Integer procedimientoId, Integer orden);
    boolean existsByProcedimientoIdAndOrdenAndIdNot(Integer procedimientoId, Integer orden, Integer id);
}
