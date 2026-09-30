package cl.casol.backend.identidad.infrastructure.persistence.repository;

import cl.casol.backend.identidad.infrastructure.persistence.entity.RolEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolJpaRepository extends JpaRepository<RolEntity, Integer> {

    List<RolEntity> findAllByActivoTrueOrderByNombreAsc();
}
