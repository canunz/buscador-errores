package cl.casol.backend.catalogo.infrastructure.web.dto;

import cl.casol.backend.catalogo.domain.Frecuencia;

/**
 * Datos que se devuelven al frontend con frecuencia.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record FrecuenciaResponse(Integer id, String nombre) {
    // Crea el objeto a partir de los datos recibidos.
    public static FrecuenciaResponse from(Frecuencia frecuencia) {
        return new FrecuenciaResponse(frecuencia.id(), frecuencia.nombre());
    }
}
