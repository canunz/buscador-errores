package cl.casol.backend.tickets.application.port.in;

import cl.casol.backend.tickets.domain.Etiqueta;
import cl.casol.backend.tickets.domain.NivelEtiqueta;

import java.util.List;

/**
 * Puerto de entrada: define la acción "gestionar etiquetas" que ofrece el módulo de tickets.
 * El controlador usa esta interfaz y el servicio la implementa.
 */
public interface GestionarEtiquetasUseCase {
    /** Soporte puede pedir también las inactivas. */
    List<Etiqueta> listar(boolean incluirInactivas, String emailAutenticado);

    // Crea el nombre.
    Etiqueta crear(String nombre, String color, NivelEtiqueta nivel, String emailAutenticado);

    // Edita la etiqueta indicada.
    Etiqueta editar(Long etiquetaId, String nombre, String color, NivelEtiqueta nivel, String emailAutenticado);

    // Cambia estado.
    Etiqueta cambiarEstado(Long etiquetaId, boolean activa, String emailAutenticado);
}
