package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.MaterialApoyo;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de material apoyo.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface MaterialApoyoRepository {
    // Busca directos por conocimiento.
    List<MaterialApoyo> buscarDirectosPorConocimiento(Integer conocimientoId);
    // Busca por paso.
    List<MaterialApoyo> buscarPorPaso(Integer pasoId);
    // Busca por id.
    Optional<MaterialApoyo> buscarPorId(Integer id);
    // Guarda el material.
    MaterialApoyo guardar(MaterialApoyo material);
    // Elimina por id.
    void eliminar(Integer id);
}
