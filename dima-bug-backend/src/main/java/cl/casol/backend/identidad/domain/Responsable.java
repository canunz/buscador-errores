package cl.casol.backend.identidad.domain;

public record Responsable(Integer id, Integer departamentoId, String nombre, String cargo,
                          String contacto, boolean activo) { }
