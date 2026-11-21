package cl.casol.backend.conocimiento.domain;

/**
 * Modelo de dominio de causa. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record Causa(Integer id, Integer conocimientoId, String descripcion, Integer orden) { }
