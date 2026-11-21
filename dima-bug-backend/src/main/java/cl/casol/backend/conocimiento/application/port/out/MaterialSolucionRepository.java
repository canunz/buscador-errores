package cl.casol.backend.conocimiento.application.port.out;
import cl.casol.backend.conocimiento.domain.MaterialSolucion;
import java.util.*;
/**
 * Repositorio Spring Data de material solución.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface MaterialSolucionRepository {
 List<MaterialSolucion> listar(Integer solucionId);
 Optional<MaterialSolucion> buscar(Integer id);
 MaterialSolucion guardar(MaterialSolucion material);
 void eliminar(Integer id);
}
