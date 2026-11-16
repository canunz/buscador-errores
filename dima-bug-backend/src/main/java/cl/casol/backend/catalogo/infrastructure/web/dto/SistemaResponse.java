package cl.casol.backend.catalogo.infrastructure.web.dto;

import cl.casol.backend.catalogo.domain.Sistema;

public record SistemaResponse(Integer id, String nombre, String descripcion) {
    public static SistemaResponse from(Sistema sistema) {
        return new SistemaResponse(sistema.id(), sistema.nombre(), sistema.descripcion());
    }
}
