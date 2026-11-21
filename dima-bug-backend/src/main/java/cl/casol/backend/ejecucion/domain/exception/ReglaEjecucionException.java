package cl.casol.backend.ejecucion.domain.exception;

/**
 * Error del módulo de ejecución de procedimientos: regla ejecución.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class ReglaEjecucionException extends RuntimeException {
    public ReglaEjecucionException(String message) { super(message); }
}
