package cl.casol.backend.identidad.domain;

public record DepartamentoContacto(Integer id, Integer departamentoId, String tipo, String valor, boolean activo) { }
