package cl.casol.backend.tickets.application.port.in;

import cl.casol.backend.tickets.domain.Etiqueta;
import cl.casol.backend.tickets.domain.NivelEtiqueta;

import java.util.List;

public interface GestionarEtiquetasUseCase {
    /** Soporte puede pedir también las inactivas. */
    List<Etiqueta> listar(boolean incluirInactivas, String emailAutenticado);

    Etiqueta crear(String nombre, String color, NivelEtiqueta nivel, String emailAutenticado);

    Etiqueta editar(Long etiquetaId, String nombre, String color, NivelEtiqueta nivel, String emailAutenticado);

    Etiqueta cambiarEstado(Long etiquetaId, boolean activa, String emailAutenticado);
}
