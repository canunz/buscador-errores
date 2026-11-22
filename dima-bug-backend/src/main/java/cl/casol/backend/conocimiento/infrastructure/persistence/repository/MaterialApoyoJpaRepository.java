package cl.casol.backend.conocimiento.infrastructure.persistence.repository;

import cl.casol.backend.conocimiento.infrastructure.persistence.entity.MaterialApoyoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Repositorio Spring Data de material apoyo.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface MaterialApoyoJpaRepository extends JpaRepository<MaterialApoyoEntity, Integer> {
    // Busca registros por conocimiento id y paso id vacío, ordenados por id de menor a mayor.
    List<MaterialApoyoEntity> findByConocimientoIdAndPasoIdIsNullOrderByIdAsc(Integer conocimientoId);
    // Busca registros por paso id y conocimiento id vacío, ordenados por id de menor a mayor.
    List<MaterialApoyoEntity> findByPasoIdAndConocimientoIdIsNullOrderByIdAsc(Integer pasoId);
}
