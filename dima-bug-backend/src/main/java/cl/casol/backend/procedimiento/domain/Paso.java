package cl.casol.backend.procedimiento.domain;

public record Paso(Integer id, Integer procedimientoId, Integer orden, String instruccion,
        boolean esCritico) { }
