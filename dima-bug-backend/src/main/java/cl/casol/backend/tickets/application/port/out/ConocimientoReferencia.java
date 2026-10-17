package cl.casol.backend.tickets.application.port.out;

/** Conocimiento vinculado a un ticket. estado: BORRADOR o PUBLICADO. */
public record ConocimientoReferencia(Integer id, String titulo, String estado) {
}