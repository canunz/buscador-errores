package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.Causa;

/**
 * Datos que se devuelven al frontend con causa.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record CausaResponse(Integer id, String descripcion, Integer orden) {
    // Crea el objeto a partir de los datos recibidos.
    public static CausaResponse from(Causa causa) {
        return new CausaResponse(causa.id(), causa.descripcion(), causa.orden());
    }
}
