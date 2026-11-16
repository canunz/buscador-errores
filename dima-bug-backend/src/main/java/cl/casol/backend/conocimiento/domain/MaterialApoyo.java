package cl.casol.backend.conocimiento.domain;

public record MaterialApoyo(Integer id, Integer conocimientoId, Integer pasoId,
                            String nombre, TipoMaterial tipo, String url) { }
