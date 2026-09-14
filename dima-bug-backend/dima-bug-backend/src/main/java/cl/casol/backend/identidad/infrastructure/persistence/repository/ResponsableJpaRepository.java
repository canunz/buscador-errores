package cl.casol.backend.identidad.infrastructure.persistence.repository;

import cl.casol.backend.identidad.infrastructure.persistence.entity.ResponsableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResponsableJpaRepository extends JpaRepository<ResponsableEntity, Integer> {
    List<ResponsableEntity> findByDepartamentoIdAndActivoTrueOrderByNombreAsc(Integer departamentoId);
}
