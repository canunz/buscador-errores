package cl.casol.backend.catalogo.infrastructure.web.dto;

import cl.casol.backend.catalogo.domain.Modulo;

public record ModuloResponse(Integer id, Integer sistemaId, String nombre) {
    public static ModuloResponse from(Modulo modulo) {
        return new ModuloResponse(modulo.id(), modulo.sistemaId(), modulo.nombre());
    }
}
