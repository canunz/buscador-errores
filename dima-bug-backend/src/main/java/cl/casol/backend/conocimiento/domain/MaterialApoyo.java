package cl.casol.backend.conocimiento.domain;

/**
 * Modelo de dominio de material apoyo. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record MaterialApoyo(Integer id, Integer conocimientoId, Integer pasoId,
                            String nombre, TipoMaterial tipo, String url) { }
