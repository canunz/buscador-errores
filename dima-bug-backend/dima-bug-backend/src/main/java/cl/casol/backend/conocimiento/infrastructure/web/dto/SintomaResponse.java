package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.Sintoma;

public record SintomaResponse(Integer id, String descripcion, Integer orden) {
    public static SintomaResponse from(Sintoma sintoma) {
        return new SintomaResponse(sintoma.id(), sintoma.descripcion(), sintoma.orden());
    }
}
