package cl.casol.backend.identidad.infrastructure.persistence.repository;

import cl.casol.backend.identidad.infrastructure.persistence.entity.DepartamentoContactoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Repositorio Spring Data de departamento contacto.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface DepartamentoContactoJpaRepository extends JpaRepository<DepartamentoContactoEntity, Integer> {
    // Busca registros por departamento id y activo en true, ordenados por id de menor a mayor.
    List<DepartamentoContactoEntity> findByDepartamentoIdAndActivoTrueOrderByIdAsc(Integer departamentoId);
}
