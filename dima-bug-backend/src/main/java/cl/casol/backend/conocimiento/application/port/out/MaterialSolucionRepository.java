package cl.casol.backend.conocimiento.application.port.out;
import cl.casol.backend.conocimiento.domain.MaterialSolucion;
import java.util.*;
public interface MaterialSolucionRepository {
 List<MaterialSolucion> listar(Integer solucionId);
 Optional<MaterialSolucion> buscar(Integer id);
 MaterialSolucion guardar(MaterialSolucion material);
 void eliminar(Integer id);
}
