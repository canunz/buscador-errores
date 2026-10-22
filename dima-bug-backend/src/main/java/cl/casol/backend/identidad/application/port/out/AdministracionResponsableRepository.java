package cl.casol.backend.identidad.application.port.out;
import cl.casol.backend.identidad.domain.Responsable;
import java.util.List;
import java.util.Optional;
public interface AdministracionResponsableRepository {
    List<Responsable> listar(Integer departamentoId);
    Optional<Responsable> buscar(Integer id);
    Optional<Responsable> bloquear(Integer id);
    Responsable guardar(Responsable responsable);
}
