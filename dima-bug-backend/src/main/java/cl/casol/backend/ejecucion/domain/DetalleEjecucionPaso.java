package cl.casol.backend.ejecucion.domain;

/**
 * Modelo de dominio de detalle ejecución paso. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record DetalleEjecucionPaso(EjecucionPaso ejecucionPaso, Integer orden,
        String instruccion, boolean esCritico) { }
