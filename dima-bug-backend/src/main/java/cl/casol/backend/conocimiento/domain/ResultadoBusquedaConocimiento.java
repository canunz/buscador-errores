package cl.casol.backend.conocimiento.domain;

/**
 * Modelo de dominio de resultado búsqueda conocimiento. Es inmutable: cada cambio crea un objeto nuevo.
 */
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
