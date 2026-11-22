package cl.casol.backend.ejecucion.domain.exception;

/**
 * Error del módulo de ejecución de procedimientos: ejecución no encontrada.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class EjecucionNoEncontradaException extends RuntimeException {
    public EjecucionNoEncontradaException(Integer id) { super("Ejecucion no encontrada: " + id); }
}
