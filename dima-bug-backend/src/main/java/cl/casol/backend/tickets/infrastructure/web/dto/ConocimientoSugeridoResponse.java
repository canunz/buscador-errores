package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.application.port.out.ConocimientoSugerido;

public record ConocimientoSugeridoResponse(Integer id, String titulo, String hardware, String sistema, String modulo) {
    public static ConocimientoSugeridoResponse from(ConocimientoSugerido s) {
        return new ConocimientoSugeridoResponse(s.id(), s.titulo(), s.hardware(), s.sistema(), s.modulo());
    }
}