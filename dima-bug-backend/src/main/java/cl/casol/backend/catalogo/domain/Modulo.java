package cl.casol.backend.catalogo.domain;

/**
 * Modelo de dominio de módulo. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record Modulo(Integer id, Integer sistemaId, String nombre, boolean activo) {
}
