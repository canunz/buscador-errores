package cl.casol.backend.catalogo.domain;

/**
 * Modelo de dominio de sistema. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record Sistema(Integer id, String nombre, String descripcion, boolean activo) {
}
