package cl.casol.backend.identidad.application.port.out;
import cl.casol.backend.identidad.domain.Responsable;
import java.util.List;
import java.util.Optional;
/**
 * Repositorio Spring Data de administración responsable.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface AdministracionResponsableRepository {
    // Lista el departamento indicado.
    List<Responsable> listar(Integer departamentoId);
    // Busca por id.
    Optional<Responsable> buscar(Integer id);
    // Bloquea por id.
    Optional<Responsable> bloquear(Integer id);
    // Guarda el responsable.
    Responsable guardar(Responsable responsable);
}
