package cl.casol.backend.conocimiento.application.port.out;
import cl.casol.backend.conocimiento.domain.Prueba;
import java.util.*;
public interface PruebaAdministrativaRepository {
 List<Prueba> listar(); Optional<Prueba> buscar(Integer id); Prueba guardar(Prueba prueba);
 void bloquear(Integer id); List<Integer> conocimientos(Integer id);
}