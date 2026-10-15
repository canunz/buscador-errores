package cl.casol.backend.conocimiento.infrastructure.persistence.repository;

import cl.casol.backend.conocimiento.infrastructure.persistence.entity.SolucionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SolucionJpaRepository extends JpaRepository<SolucionEntity, Integer> {
    List<SolucionEntity> findByConocimientoIdOrderByOrdenAsc(Integer conocimientoId);
    List<SolucionEntity> findByConocimientoIdAndActivoTrueOrderByOrdenAsc(Integer conocimientoId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from SolucionEntity s where s.conocimientoId = :id order by s.id")
    List<SolucionEntity> bloquearPorConocimiento(@org.springframework.data.repository.query.Param("id") Integer id);
}
