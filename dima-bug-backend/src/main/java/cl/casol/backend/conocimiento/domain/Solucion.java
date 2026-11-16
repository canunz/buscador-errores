package cl.casol.backend.conocimiento.domain;

public record Solucion(Integer id, Integer conocimientoId, String descripcion, TipoSolucion tipo,
        Integer orden, String titulo, boolean activo, boolean autoservicio) {
    // Compatibilidad con registros históricos sin título y fixtures anteriores.
    public Solucion(Integer id, Integer conocimientoId, String descripcion, TipoSolucion tipo, Integer orden) {
        this(id, conocimientoId, descripcion, tipo, orden, null, true, false);
    }
}
