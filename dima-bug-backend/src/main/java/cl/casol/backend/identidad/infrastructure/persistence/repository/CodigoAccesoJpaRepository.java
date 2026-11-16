package cl.casol.backend.identidad.infrastructure.persistence.repository;

import cl.casol.backend.identidad.infrastructure.persistence.entity.CodigoAccesoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CodigoAccesoJpaRepository extends JpaRepository<CodigoAccesoEntity, Long> {

    // Lecturas actuales, no snapshots anteriores al bloqueo del usuario en MySQL REPEATABLE READ.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.List<CodigoAccesoEntity> findByUsuarioIdAndCreadoEnGreaterThanEqual(Integer usuarioId, java.time.LocalDateTime desde);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<CodigoAccesoEntity> findFirstByUsuarioIdOrderByIdDesc(Integer usuarioId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<CodigoAccesoEntity> findFirstByUsuarioIdAndUsadoFalseOrderByIdDesc(Integer usuarioId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CodigoAccesoEntity c set c.usado = true where c.usuarioId = :usuarioId and c.usado = false")
    int invalidarPendientes(@Param("usuarioId") Integer usuarioId);
}
