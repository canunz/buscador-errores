package cl.casol.backend.conocimiento.application.port.out;
import cl.casol.backend.conocimiento.domain.Prueba;
import java.util.*;
/**
 * Repositorio Spring Data de prueba administrativa.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface PruebaAdministrativaRepository {
 List<Prueba> listar(); Optional<Prueba> buscar(Integer id); Prueba guardar(Prueba prueba);
 void bloquear(Integer id); List<Integer> conocimientos(Integer id);
}