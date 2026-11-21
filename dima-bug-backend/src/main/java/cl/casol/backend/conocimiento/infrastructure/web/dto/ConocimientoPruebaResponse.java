package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.ConocimientoPrueba;

/**
 * Datos que se devuelven al frontend con conocimiento prueba.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record ConocimientoPruebaResponse(Integer id, String descripcion, String resultadoEsperado, Integer orden, boolean activa) {
    // Crea el objeto a partir de los datos recibidos.
    public static ConocimientoPruebaResponse from(ConocimientoPrueba asociacion) {
        return new ConocimientoPruebaResponse(asociacion.prueba().id(), asociacion.prueba().descripcion(),
                asociacion.prueba().resultadoEsperado(), asociacion.orden(), asociacion.prueba().activa());
    }
}
