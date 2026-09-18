package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.ConocimientoPrueba;

public record ConocimientoPruebaResponse(Integer id, String descripcion, String resultadoEsperado, Integer orden) {
    public static ConocimientoPruebaResponse from(ConocimientoPrueba asociacion) {
        return new ConocimientoPruebaResponse(asociacion.prueba().id(), asociacion.prueba().descripcion(),
                asociacion.prueba().resultadoEsperado(), asociacion.orden());
    }
}
