package cl.casol.backend.conocimiento.domain;

/**
 * Modelo de dominio de conocimiento prueba. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record ConocimientoPrueba(Integer conocimientoId, Prueba prueba, Integer orden) { }
