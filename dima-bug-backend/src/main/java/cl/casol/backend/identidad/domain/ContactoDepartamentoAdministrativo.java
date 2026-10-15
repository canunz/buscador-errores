package cl.casol.backend.identidad.domain;
public record ContactoDepartamentoAdministrativo(Integer id, Integer departamentoId, String tipo, String valor, boolean activo, Long version) { }
