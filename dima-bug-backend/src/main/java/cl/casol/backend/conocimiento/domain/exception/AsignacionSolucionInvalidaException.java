package cl.casol.backend.conocimiento.domain.exception;

/**
 * Error del módulo de conocimiento: asignación solución inválida.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class AsignacionSolucionInvalidaException extends RuntimeException {
    public AsignacionSolucionInvalidaException(String message) { super(message); }
}
