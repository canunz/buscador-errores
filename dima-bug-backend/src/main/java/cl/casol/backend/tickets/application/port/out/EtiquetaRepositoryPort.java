package cl.casol.backend.tickets.application.port.out;

import cl.casol.backend.tickets.domain.Etiqueta;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EtiquetaRepositoryPort {
    List<Etiqueta> listar(boolean incluirInactivas);
    Optional<Etiqueta> buscarPorId(Long id);
    List<Etiqueta> buscarPorIds(Collection<Long> ids);
    boolean existeNombre(String nombre, Long excluirId);
    Etiqueta guardar(Etiqueta etiqueta);
}