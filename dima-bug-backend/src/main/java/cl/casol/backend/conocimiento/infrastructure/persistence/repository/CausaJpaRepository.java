package cl.casol.backend.conocimiento.infrastructure.persistence.repository;

import cl.casol.backend.conocimiento.infrastructure.persistence.entity.CausaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Repositorio Spring Data de causa.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface CausaJpaRepository extends JpaRepository<CausaEntity, Integer> {
    // Busca registros por conocimiento id, ordenados por orden de menor a mayor.
    List<CausaEntity> findByConocimientoIdOrderByOrdenAsc(Integer conocimientoId);
}
