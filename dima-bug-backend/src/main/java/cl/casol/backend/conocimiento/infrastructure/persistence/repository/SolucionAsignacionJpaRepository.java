package cl.casol.backend.conocimiento.infrastructure.persistence.repository;

import cl.casol.backend.conocimiento.infrastructure.persistence.entity.SolucionAsignacionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Repositorio Spring Data de solución asignación.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface SolucionAsignacionJpaRepository extends JpaRepository<SolucionAsignacionEntity, Integer> {
    // Busca registros por solución id, ordenados por principal de mayor a menor id de menor a mayor.
    List<SolucionAsignacionEntity> findBySolucionIdOrderByPrincipalDescIdAsc(Integer solucionId);
}
