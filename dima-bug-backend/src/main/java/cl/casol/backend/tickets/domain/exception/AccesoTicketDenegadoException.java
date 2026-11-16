package cl.casol.backend.tickets.domain.exception;

/** El rol de la persona no permite la acción (respuesta 403). */
public class AccesoTicketDenegadoException extends RuntimeException {
    public AccesoTicketDenegadoException(String mensaje) {
        super(mensaje);
    }
}