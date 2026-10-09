package cl.casol.backend.procedimiento.infrastructure.persistence.repository;

import cl.casol.backend.procedimiento.infrastructure.persistence.entity.ProcedimientoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ProcedimientoJpaRepository extends JpaRepository<ProcedimientoEntity, Integer> {
    @Query("""
            select p from ProcedimientoEntity p
            join fetch p.creadoPor creador
            join fetch creador.rol
            left join fetch p.modificadoPor modificador
            left join fetch modificador.rol
            order by p.fechaCreacion desc
            """)
    List<ProcedimientoEntity> listarConRelaciones();
}
