package cl.casol.backend.identidad.domain;
/**
 * Modelo de dominio de contacto departamento administrativo. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record ContactoDepartamentoAdministrativo(Integer id, Integer departamentoId, String tipo, String valor, boolean activo, Long version) { }
