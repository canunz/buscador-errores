package cl.casol.backend.conocimiento.api;

/** Resumen de un conocimiento para otros módulos. estado: BORRADOR o PUBLICADO. */
public record ConocimientoPublico(Integer id, String titulo, String estado, String hardware, String sistema,
        String modulo) {
}