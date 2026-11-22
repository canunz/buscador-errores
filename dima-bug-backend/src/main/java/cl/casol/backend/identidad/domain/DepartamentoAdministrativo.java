package cl.casol.backend.identidad.domain;
/**
 * Modelo de dominio de departamento administrativo. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record DepartamentoAdministrativo(Integer id, String nombre, boolean activo, Long version) { }
