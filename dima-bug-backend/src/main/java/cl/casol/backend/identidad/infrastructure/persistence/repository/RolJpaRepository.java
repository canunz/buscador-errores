package cl.casol.backend.identidad.infrastructure.persistence.repository;

import cl.casol.backend.identidad.infrastructure.persistence.entity.RolEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositorio Spring Data de rol.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface RolJpaRepository extends JpaRepository<RolEntity, Integer> {

    // Busca todos los registros por activo en true, ordenados por nombre de menor a mayor.
    List<RolEntity> findAllByActivoTrueOrderByNombreAsc();
}
