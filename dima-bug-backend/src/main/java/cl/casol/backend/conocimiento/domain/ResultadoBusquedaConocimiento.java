package cl.casol.backend.conocimiento.domain;

public record ResultadoBusquedaConocimiento(
        Integer id,
        String titulo,
        Integer hardwareId,
        String hardwareNombre,
        Integer sistemaId,
        String sistemaNombre,
        Integer moduloId,
        String moduloNombre,
        Integer frecuenciaId,
        String frecuenciaNombre,
        Double relevancia) {
}
