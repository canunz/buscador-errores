package cl.casol.backend.seguimiento.domain.exception;

/**
 * Error del módulo de seguimiento: estado conocimiento no evaluable.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class EstadoConocimientoNoEvaluableException extends RuntimeException {
    public EstadoConocimientoNoEvaluableException() {
        super("Solo se pueden registrar resultados en conocimientos PUBLICADOS");
    }
}
