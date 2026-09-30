package cl.casol.backend.ejecucion.infrastructure.persistence.repository;
import cl.casol.backend.ejecucion.infrastructure.persistence.entity.EjecucionPasoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EjecucionPasoJpaRepository extends JpaRepository<EjecucionPasoEntity,Integer> {
    List<EjecucionPasoEntity> findByEjecucionId(Integer ejecucionId);
}
