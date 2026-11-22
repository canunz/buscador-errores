package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.Prueba;

/**
 * Datos que se devuelven al frontend con prueba.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record PruebaResponse(Integer id, String descripcion, String resultadoEsperado) {
    // Crea el objeto a partir de los datos recibidos.
    public static PruebaResponse from(Prueba prueba) {
        return new PruebaResponse(prueba.id(), prueba.descripcion(), prueba.resultadoEsperado());
    }
}
