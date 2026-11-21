package cl.casol.backend.catalogo.domain;

/**
 * Modelo de dominio de hardware. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record Hardware(Integer id, String nombre, String sistemaOperativo, boolean activo) {
}
