package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.Causa;

public record CausaResponse(Integer id, String descripcion, Integer orden) {
    public static CausaResponse from(Causa causa) {
        return new CausaResponse(causa.id(), causa.descripcion(), causa.orden());
    }
}
