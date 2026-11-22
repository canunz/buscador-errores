package cl.casol.backend.conocimiento.infrastructure.persistence.repository;

import cl.casol.backend.conocimiento.infrastructure.persistence.entity.ConocimientoEntity;
import cl.casol.backend.conocimiento.domain.EstadoConocimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de conocimiento.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface ConocimientoJpaRepository extends JpaRepository<ConocimientoEntity, Integer> {
    // Bloquea por id.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConocimientoEntity c where c.id = :id")
    java.util.Optional<ConocimientoEntity> bloquearPorId(@Param("id") Integer id);

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
    // Lista con relaciones.
    List<ConocimientoEntity> listarConRelaciones(@Param("estado") EstadoConocimiento estado);

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
            where c.id = :id
            """)
    // Busca con relaciones por id.
    Optional<ConocimientoEntity> buscarConRelacionesPorId(@Param("id") Integer id);

    // Devuelve resumen por id.
    @Query("select c.id, c.titulo, c.estado from ConocimientoEntity c where c.id = :id and c.estado <> :eliminado")
    java.util.List<Object[]> resumenPorId(@Param("id") Integer id, @Param("eliminado") EstadoConocimiento eliminado);
}
