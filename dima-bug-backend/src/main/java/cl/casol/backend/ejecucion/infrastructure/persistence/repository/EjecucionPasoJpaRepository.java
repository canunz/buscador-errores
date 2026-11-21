package cl.casol.backend.ejecucion.infrastructure.persistence.repository;
import cl.casol.backend.ejecucion.infrastructure.persistence.entity.EjecucionPasoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
/**
 * Repositorio Spring Data de ejecución paso.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface EjecucionPasoJpaRepository extends JpaRepository<EjecucionPasoEntity,Integer> {
    // Busca registros por ejecución id.
    List<EjecucionPasoEntity> findByEjecucionId(Integer ejecucionId);
}
