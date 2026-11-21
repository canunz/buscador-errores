package cl.casol.backend.tickets.application.port.out;

import cl.casol.backend.tickets.domain.Etiqueta;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida de etiqueta.
 * Dice qué necesita el módulo desde afuera, sin depender de cómo se hace (base de datos, correo, etc.).
 */
public interface EtiquetaRepositoryPort {
    // Lista las incluir inactivas.
    List<Etiqueta> listar(boolean incluirInactivas);
    // Busca por id.
    Optional<Etiqueta> buscarPorId(Long id);
    // Busca por ids.
    List<Etiqueta> buscarPorIds(Collection<Long> ids);
    // Indica si existe nombre.
    boolean existeNombre(String nombre, Long excluirId);
    // Guarda la etiqueta.
    Etiqueta guardar(Etiqueta etiqueta);
}