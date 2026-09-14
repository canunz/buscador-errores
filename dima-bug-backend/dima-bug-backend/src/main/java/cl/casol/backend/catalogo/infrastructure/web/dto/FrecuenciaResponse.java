package cl.casol.backend.catalogo.infrastructure.web.dto;

import cl.casol.backend.catalogo.domain.Frecuencia;

public record FrecuenciaResponse(Integer id, String nombre) {
    public static FrecuenciaResponse from(Frecuencia frecuencia) {
        return new FrecuenciaResponse(frecuencia.id(), frecuencia.nombre());
    }
}
