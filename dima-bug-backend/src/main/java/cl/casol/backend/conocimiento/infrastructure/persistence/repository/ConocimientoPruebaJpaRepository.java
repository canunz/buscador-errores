package cl.casol.backend.conocimiento.infrastructure.persistence.repository;

import cl.casol.backend.conocimiento.infrastructure.persistence.entity.ConocimientoPruebaEntity;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.ConocimientoPruebaId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Repositorio Spring Data de conocimiento prueba.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface ConocimientoPruebaJpaRepository
        extends JpaRepository<ConocimientoPruebaEntity, ConocimientoPruebaId> {
    // Busca registros por id conocimiento id, ordenados por orden de menor a mayor id prueba id de menor a mayor.
    List<ConocimientoPruebaEntity> findByIdConocimientoIdOrderByOrdenAscIdPruebaIdAsc(Integer conocimientoId);
    // Busca registros por id conocimiento id y prueba activa en true, ordenados por orden de menor a mayor.
    List<ConocimientoPruebaEntity> findByIdConocimientoIdAndPruebaActivaTrueOrderByOrdenAsc(
            Integer conocimientoId);
}
