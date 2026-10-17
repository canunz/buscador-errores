package cl.casol.backend.tickets.application.port.out;

/** Nombres legibles de la clasificación de un ticket (pueden venir null). */
public record ClasificacionTicket(String hardware, String sistema, String modulo) {
    public static final ClasificacionTicket VACIA = new ClasificacionTicket(null, null, null);
}