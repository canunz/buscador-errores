package cl.casol.backend.conocimiento.domain;

/**
 * Modelo de dominio de síntoma. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record Sintoma(Integer id, Integer conocimientoId, String descripcion, Integer orden) { }
