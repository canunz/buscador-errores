package cl.casol.backend.identidad.domain;

/**
 * Modelo de dominio de departamento contacto. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record DepartamentoContacto(Integer id, Integer departamentoId, String tipo, String valor, boolean activo) { }
