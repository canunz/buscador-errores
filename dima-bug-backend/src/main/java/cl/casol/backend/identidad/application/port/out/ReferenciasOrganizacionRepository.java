package cl.casol.backend.identidad.application.port.out;
import java.util.List;
/**
 * Repositorio Spring Data de referencias organización.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface ReferenciasOrganizacionRepository { List<Integer> conocimientos(Integer departamentoId); }
