package cl.casol.backend.tickets.domain;

/**
 * Cuántos tickets ha creado una persona.
 * Se usa en el panel del administrador para ver quién reporta más problemas.
 *
 * @param usuarioId id de la persona que creó los tickets
 * @param nombre    nombre de la persona
 * @param email     correo de la persona
 * @param total     todos los tickets que ha creado
 * @param activos   los que siguen abiertos, en progreso o en espera
 * @param resueltos los que ya están resueltos o cerrados
 */
public record TicketsPorUsuario(Integer usuarioId, String nombre, String email,
        long total, long activos, long resueltos) {
}
