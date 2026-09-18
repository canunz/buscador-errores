package cl.casol.backend.identidad.infrastructure.persistence.repository;

import cl.casol.backend.identidad.infrastructure.persistence.entity.DepartamentoContactoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DepartamentoContactoJpaRepository extends JpaRepository<DepartamentoContactoEntity, Integer> {
    List<DepartamentoContactoEntity> findByDepartamentoIdAndActivoTrueOrderByIdAsc(Integer departamentoId);
}
