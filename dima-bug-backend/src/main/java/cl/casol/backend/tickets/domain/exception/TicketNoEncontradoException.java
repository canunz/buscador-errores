package cl.casol.backend.tickets.domain.exception;

/**
 * Error del módulo de tickets: ticket no encontrado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class TicketNoEncontradoException extends RuntimeException {
    public TicketNoEncontradoException(Long id) {
        super("No se encontró el ticket " + id);
    }
}