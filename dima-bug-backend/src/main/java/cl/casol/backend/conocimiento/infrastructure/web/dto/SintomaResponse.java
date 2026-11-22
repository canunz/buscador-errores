package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.Sintoma;

/**
 * Datos que se devuelven al frontend con síntoma.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record SintomaResponse(Integer id, String descripcion, Integer orden) {
    // Crea el objeto a partir de los datos recibidos.
    public static SintomaResponse from(Sintoma sintoma) {
        return new SintomaResponse(sintoma.id(), sintoma.descripcion(), sintoma.orden());
    }
}
