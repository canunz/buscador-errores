package cl.casol.backend.identidad.domain;

/**
 * Modelo de dominio de departamento. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record Departamento(Integer id, String nombre, boolean activo) { }
