package cl.casol.backend.tickets.domain.exception;

/** Datos que no cumplen una regla del ticket (respuesta 400). */
public class TicketInvalidoException extends RuntimeException {
    public TicketInvalidoException(String mensaje) {
        super(mensaje);
    }
}