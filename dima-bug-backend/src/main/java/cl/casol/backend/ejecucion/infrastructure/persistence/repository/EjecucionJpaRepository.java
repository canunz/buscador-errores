package cl.casol.backend.ejecucion.infrastructure.persistence.repository;
import cl.casol.backend.ejecucion.infrastructure.persistence.entity.EjecucionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EjecucionJpaRepository extends JpaRepository<EjecucionEntity,Integer> {
    List<EjecucionEntity> findByUsuarioIdOrderByFechaInicioDesc(Integer usuarioId);
    List<EjecucionEntity> findAllByOrderByFechaInicioDesc();
}
