package cl.casol.backend.conocimiento.infrastructure.persistence.repository;

import cl.casol.backend.conocimiento.infrastructure.persistence.entity.SintomaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Repositorio Spring Data de síntoma.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface SintomaJpaRepository extends JpaRepository<SintomaEntity, Integer> {
    // Busca registros por conocimiento id, ordenados por orden de menor a mayor.
    List<SintomaEntity> findByConocimientoIdOrderByOrdenAsc(Integer conocimientoId);
}
