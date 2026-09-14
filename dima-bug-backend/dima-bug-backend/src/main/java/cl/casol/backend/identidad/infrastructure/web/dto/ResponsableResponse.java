package cl.casol.backend.identidad.infrastructure.web.dto;

import cl.casol.backend.identidad.domain.Responsable;

public record ResponsableResponse(Integer id, String nombre, String cargo, String contacto) {
    public static ResponsableResponse from(Responsable responsable) {
        return new ResponsableResponse(responsable.id(), responsable.nombre(), responsable.cargo(), responsable.contacto());
    }
}
