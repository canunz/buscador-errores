package cl.casol.backend.conocimiento.domain;

public record Solucion(Integer id, Integer conocimientoId, String descripcion, TipoSolucion tipo, Integer orden) { }
