package cl.casol.backend.ejecucion.domain;

public record DetalleEjecucionPaso(EjecucionPaso ejecucionPaso, Integer orden,
        String instruccion, boolean esCritico) { }
