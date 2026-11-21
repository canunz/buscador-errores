package cl.casol.backend.conocimiento.domain;

/**
 * Modelo de dominio de prueba. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record Prueba(Integer id, String descripcion, String resultadoEsperado, boolean activa) { }
