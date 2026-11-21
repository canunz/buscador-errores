package cl.casol.backend.ejecucion.infrastructure.persistence.repository;
import cl.casol.backend.ejecucion.infrastructure.persistence.entity.EjecucionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
/**
 * Repositorio Spring Data de ejecución.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface EjecucionJpaRepository extends JpaRepository<EjecucionEntity,Integer> {
    // Busca registros por usuario id, ordenados por fecha inicio de mayor a menor.
    List<EjecucionEntity> findByUsuarioIdOrderByFechaInicioDesc(Integer usuarioId);
    // Busca todos, ordenados por fecha inicio de mayor a menor.
    List<EjecucionEntity> findAllByOrderByFechaInicioDesc();
}
