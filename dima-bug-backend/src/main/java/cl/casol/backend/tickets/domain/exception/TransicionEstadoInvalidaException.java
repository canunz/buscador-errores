package cl.casol.backend.tickets.domain.exception;

import cl.casol.backend.tickets.domain.EstadoTicket;

/**
 * Error del módulo de tickets: transición estado inválida.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class TransicionEstadoInvalidaException extends RuntimeException {
    public TransicionEstadoInvalidaException(EstadoTicket desde, EstadoTicket hacia) {
        super("No se puede pasar un ticket de " + desde + " a " + hacia);
    }

    public TransicionEstadoInvalidaException(String mensaje) {
        super(mensaje);
    }
}