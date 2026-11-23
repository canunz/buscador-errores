package cl.casol.backend.tickets.application.port.out;
import java.util.List;
import java.util.Optional;
public interface CatalogoSolicitantePort {
    record Opcion(Integer id, String nombre) {}
    List<Opcion> sucursales();
    List<Opcion> departamentos();
    Optional<Opcion> sucursalActiva(Integer id);
    Optional<Opcion> departamentoActivo(Integer id);
}
