package cl.casol.backend.procedimiento.domain.exception;

/**
 * Error del módulo de procedimientos: estado procedimiento inválido.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class EstadoProcedimientoInvalidoException extends RuntimeException {
    public EstadoProcedimientoInvalidoException(String mensaje) { super(mensaje); }
}
