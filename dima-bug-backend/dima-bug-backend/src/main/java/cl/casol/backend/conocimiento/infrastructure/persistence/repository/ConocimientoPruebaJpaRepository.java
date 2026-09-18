package cl.casol.backend.conocimiento.infrastructure.persistence.repository;

import cl.casol.backend.conocimiento.infrastructure.persistence.entity.ConocimientoPruebaEntity;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.ConocimientoPruebaId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConocimientoPruebaJpaRepository
        extends JpaRepository<ConocimientoPruebaEntity, ConocimientoPruebaId> {
    List<ConocimientoPruebaEntity> findByIdConocimientoIdAndPruebaActivaTrueOrderByOrdenAsc(
            Integer conocimientoId);
}
