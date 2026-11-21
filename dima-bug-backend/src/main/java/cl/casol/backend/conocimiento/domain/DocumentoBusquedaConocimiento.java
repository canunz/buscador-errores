package cl.casol.backend.conocimiento.domain;

import java.util.List;

/**
 * Modelo de dominio de documento búsqueda conocimiento. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record DocumentoBusquedaConocimiento(
        List<String> conocimientoYClasificacion,
        List<String> sintomas,
        List<String> causas,
        List<String> pruebas,
        List<String> soluciones,
        List<String> asignaciones,
        List<String> materiales) {
}
