package cl.casol.backend.conocimiento.infrastructure.persistence.repository;

import cl.casol.backend.conocimiento.infrastructure.persistence.entity.SolucionAsignacionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SolucionAsignacionJpaRepository extends JpaRepository<SolucionAsignacionEntity, Integer> {
    List<SolucionAsignacionEntity> findBySolucionIdOrderByPrincipalDescIdAsc(Integer solucionId);
}
