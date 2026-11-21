package cl.casol.backend.ejecucion.domain.exception;

/**
 * Error del módulo de ejecución de procedimientos: ejecución paso no encontrado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class EjecucionPasoNoEncontradoException extends RuntimeException {
    public EjecucionPasoNoEncontradoException(Integer id) { super("Paso de ejecucion no encontrado: " + id); }
}
