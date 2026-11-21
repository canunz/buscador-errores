package cl.casol.backend.identidad.infrastructure.persistence.repository;

import cl.casol.backend.identidad.infrastructure.persistence.entity.SucursalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/**
 * Repositorio Spring Data de sucursal.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface SucursalJpaRepository extends JpaRepository<SucursalEntity,Integer> {
    // Busca todos, ordenados por código de menor a mayor.
    List<SucursalEntity> findAllByOrderByCodigoAsc();
    // Busca registros por código.
    Optional<SucursalEntity> findByCodigo(String codigo);
}
