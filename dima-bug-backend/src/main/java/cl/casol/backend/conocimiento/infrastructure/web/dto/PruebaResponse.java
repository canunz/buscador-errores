package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.Prueba;

public record PruebaResponse(Integer id, String descripcion, String resultadoEsperado) {
    public static PruebaResponse from(Prueba prueba) {
        return new PruebaResponse(prueba.id(), prueba.descripcion(), prueba.resultadoEsperado());
    }
}
