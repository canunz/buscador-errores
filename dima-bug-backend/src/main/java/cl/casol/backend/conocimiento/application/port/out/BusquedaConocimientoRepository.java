package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.ResultadoBusquedaConocimiento;
import java.util.List;

/**
 * Repositorio Spring Data de búsqueda conocimiento.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface BusquedaConocimientoRepository {
    // Busca autoservicio.
    List<ResultadoBusquedaConocimiento> buscarAutoservicio(String texto);
    // Busca el texto.
    List<ResultadoBusquedaConocimiento> buscar(String texto, Integer hardwareId, Integer sistemaId,
            Integer moduloId, Integer frecuenciaId);
}
