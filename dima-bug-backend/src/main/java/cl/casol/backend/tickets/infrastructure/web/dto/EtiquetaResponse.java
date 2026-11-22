package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.domain.Etiqueta;
import cl.casol.backend.tickets.domain.NivelEtiqueta;

/**
 * Datos que se devuelven al frontend con etiqueta.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record EtiquetaResponse(Long id, String nombre, String color, boolean activa, NivelEtiqueta nivel) {
    // Crea el objeto a partir de los datos recibidos.
    public static EtiquetaResponse from(Etiqueta e) {
        return new EtiquetaResponse(e.getId(), e.getNombre(), e.getColor(), e.isActiva(), e.getNivel());
    }
}
