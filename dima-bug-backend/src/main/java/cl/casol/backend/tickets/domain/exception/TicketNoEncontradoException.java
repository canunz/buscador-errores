package cl.casol.backend.tickets.domain.exception;

public class TicketNoEncontradoException extends RuntimeException {
    public TicketNoEncontradoException(Long id) {
        super("No se encontró el ticket " + id);
    }
}