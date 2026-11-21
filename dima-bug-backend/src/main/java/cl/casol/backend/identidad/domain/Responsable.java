package cl.casol.backend.identidad.domain;

/**
 * Modelo de dominio de responsable. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record Responsable(Integer id, Integer departamentoId, String nombre, String cargo,
                          String contacto, boolean activo) { }
