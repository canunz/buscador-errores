package cl.casol.backend.identidad.infrastructure.persistence.repository;

import cl.casol.backend.identidad.infrastructure.persistence.entity.ResponsableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de responsable.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface ResponsableJpaRepository extends JpaRepository<ResponsableEntity, Integer> {
    // Busca registros por departamento id y activo en true, ordenados por nombre de menor a mayor.
    List<ResponsableEntity> findByDepartamentoIdAndActivoTrueOrderByNombreAsc(Integer departamentoId);
    // Busca registros por id y activo en true.
    Optional<ResponsableEntity> findByIdAndActivoTrue(Integer id);
}
