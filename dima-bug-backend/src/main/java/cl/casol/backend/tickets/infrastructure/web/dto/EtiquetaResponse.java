package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.domain.Etiqueta;
import cl.casol.backend.tickets.domain.NivelEtiqueta;

public record EtiquetaResponse(Long id, String nombre, String color, boolean activa, NivelEtiqueta nivel) {
    public static EtiquetaResponse from(Etiqueta e) {
        return new EtiquetaResponse(e.getId(), e.getNombre(), e.getColor(), e.isActiva(), e.getNivel());
    }
}
