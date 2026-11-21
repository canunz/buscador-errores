package cl.casol.backend.identidad.infrastructure.persistence.repository;

import cl.casol.backend.identidad.infrastructure.persistence.entity.DepartamentoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de departamento.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface DepartamentoJpaRepository extends JpaRepository<DepartamentoEntity, Integer> {
    // Busca registros por activo en true, ordenados por nombre de menor a mayor.
    List<DepartamentoEntity> findByActivoTrueOrderByNombreAsc();
    // Busca registros por id y activo en true.
    Optional<DepartamentoEntity> findByIdAndActivoTrue(Integer id);
}
