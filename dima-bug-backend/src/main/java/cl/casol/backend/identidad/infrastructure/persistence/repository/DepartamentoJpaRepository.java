package cl.casol.backend.identidad.infrastructure.persistence.repository;

import cl.casol.backend.identidad.infrastructure.persistence.entity.DepartamentoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DepartamentoJpaRepository extends JpaRepository<DepartamentoEntity, Integer> {
    List<DepartamentoEntity> findByActivoTrueOrderByNombreAsc();
    Optional<DepartamentoEntity> findByIdAndActivoTrue(Integer id);
}
