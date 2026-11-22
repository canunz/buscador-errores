package cl.casol.backend.catalogo.application.port.out;

import cl.casol.backend.catalogo.domain.Frecuencia;

import java.util.Optional;
import java.util.List;

/**
 * Repositorio Spring Data de frecuencia.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface FrecuenciaRepository {
    // Busca activa por id.
    Optional<Frecuencia> buscarActivaPorId(Integer id);
    // Busca activas ordenadas.
    List<Frecuencia> buscarActivasOrdenadas();
}
