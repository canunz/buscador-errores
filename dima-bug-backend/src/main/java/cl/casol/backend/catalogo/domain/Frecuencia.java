package cl.casol.backend.catalogo.domain;

/**
 * Modelo de dominio de frecuencia. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record Frecuencia(Integer id, String nombre, boolean activo) {
}
