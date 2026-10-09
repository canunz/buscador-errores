package cl.casol.backend.conocimiento.infrastructure.persistence.repository;

import cl.casol.backend.conocimiento.infrastructure.persistence.entity.ConocimientoEntity;
import cl.casol.backend.conocimiento.domain.EstadoConocimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConocimientoJpaRepository extends JpaRepository<ConocimientoEntity, Integer> {
    @Query("""
            select c from ConocimientoEntity c
            left join fetch c.hardware
            left join fetch c.sistema
            left join fetch c.modulo modulo
            left join fetch modulo.sistema
            left join fetch c.frecuencia
            join fetch c.creadoPor creador
            join fetch creador.rol
            left join fetch c.modificadoPor modificador
            left join fetch modificador.rol
            where c.estado <> :estado
            order by c.fechaCreacion desc
            """)
    List<ConocimientoEntity> listarConRelaciones(@Param("estado") EstadoConocimiento estado);
}
