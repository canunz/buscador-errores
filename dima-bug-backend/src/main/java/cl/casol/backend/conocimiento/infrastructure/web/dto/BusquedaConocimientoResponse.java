package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.ResultadoBusquedaConocimiento;

public record BusquedaConocimientoResponse(
        Integer id, String titulo,
        Integer hardwareId, String hardwareNombre,
        Integer sistemaId, String sistemaNombre,
        Integer moduloId, String moduloNombre,
        Integer frecuenciaId, String frecuenciaNombre,
        Double relevancia) {

    public static BusquedaConocimientoResponse from(ResultadoBusquedaConocimiento resultado) {
        return new BusquedaConocimientoResponse(resultado.id(), resultado.titulo(), resultado.hardwareId(),
                resultado.hardwareNombre(), resultado.sistemaId(), resultado.sistemaNombre(), resultado.moduloId(),
                resultado.moduloNombre(), resultado.frecuenciaId(), resultado.frecuenciaNombre(),
                resultado.relevancia());
    }
}
