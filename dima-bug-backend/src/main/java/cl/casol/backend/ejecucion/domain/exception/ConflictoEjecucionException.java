package cl.casol.backend.ejecucion.domain.exception;

/**
 * Error del módulo de ejecución de procedimientos: conflicto ejecución.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class ConflictoEjecucionException extends RuntimeException {
    public ConflictoEjecucionException(String mensaje) { super(mensaje); }
}
