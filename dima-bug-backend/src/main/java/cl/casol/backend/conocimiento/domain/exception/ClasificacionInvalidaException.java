package cl.casol.backend.conocimiento.domain.exception;

/**
 * Error del módulo de conocimiento: clasificación inválida.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class ClasificacionInvalidaException extends RuntimeException {
    public ClasificacionInvalidaException(String message) { super(message); }
}
