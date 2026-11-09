package cl.casol.backend.identidad.infrastructure.persistence.repository;

import cl.casol.backend.identidad.infrastructure.persistence.entity.SucursalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SucursalJpaRepository extends JpaRepository<SucursalEntity,Integer> {
    List<SucursalEntity> findAllByOrderByCodigoAsc();
    Optional<SucursalEntity> findByCodigo(String codigo);
}
