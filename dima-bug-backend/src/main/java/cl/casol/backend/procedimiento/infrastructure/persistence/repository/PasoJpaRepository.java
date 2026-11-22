package cl.casol.backend.procedimiento.infrastructure.persistence.repository;

import cl.casol.backend.procedimiento.infrastructure.persistence.entity.PasoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Repositorio Spring Data de paso.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface PasoJpaRepository extends JpaRepository<PasoEntity, Integer> {
    // Busca registros por procedimiento id, ordenados por orden de menor a mayor.
    List<PasoEntity> findByProcedimientoIdOrderByOrdenAsc(Integer procedimientoId);
    // Revisa si existe algún registro por procedimiento id y orden.
    boolean existsByProcedimientoIdAndOrden(Integer procedimientoId, Integer orden);
    // Revisa si existe algún registro por procedimiento id y orden y id distinto de.
    boolean existsByProcedimientoIdAndOrdenAndIdNot(Integer procedimientoId, Integer orden, Integer id);
}
