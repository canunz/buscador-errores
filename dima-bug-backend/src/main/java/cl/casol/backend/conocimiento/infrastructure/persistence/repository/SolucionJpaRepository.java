package cl.casol.backend.conocimiento.infrastructure.persistence.repository;

import cl.casol.backend.conocimiento.infrastructure.persistence.entity.SolucionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Repositorio Spring Data de solución.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface SolucionJpaRepository extends JpaRepository<SolucionEntity, Integer> {
    @org.springframework.data.jpa.repository.Query("select s from SolucionEntity s, ConocimientoEntity c where s.conocimientoId=c.id and c.estado=cl.casol.backend.conocimiento.domain.EstadoConocimiento.PUBLICADO and s.activo=true and s.autoservicio=true and (:q='' or locate(lower(:q),lower(concat(coalesce(s.titulo,''),' ',s.descripcion)))>0) order by s.id")
    List<SolucionEntity> publicas(@org.springframework.data.repository.query.Param("q") String q,org.springframework.data.domain.Pageable pagina);
    // Busca registros por conocimiento id y activo en true y autoservicio en true, ordenados por orden de menor a mayor.
    List<SolucionEntity> findByConocimientoIdAndActivoTrueAndAutoservicioTrueOrderByOrdenAsc(Integer conocimientoId);
    // Busca registros por conocimiento id, ordenados por orden de menor a mayor.
    List<SolucionEntity> findByConocimientoIdOrderByOrdenAsc(Integer conocimientoId);
    // Busca registros por conocimiento id y activo en true, ordenados por orden de menor a mayor.
    List<SolucionEntity> findByConocimientoIdAndActivoTrueOrderByOrdenAsc(Integer conocimientoId);
    // Bloquea por conocimiento.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from SolucionEntity s where s.conocimientoId = :id order by s.id")
    List<SolucionEntity> bloquearPorConocimiento(@org.springframework.data.repository.query.Param("id") Integer id);
}
