package cl.casol.backend.procedimiento.domain.exception;

/**
 * Error del módulo de procedimientos: procedimiento no encontrado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class ProcedimientoNoEncontradoException extends RuntimeException {
    public ProcedimientoNoEncontradoException(Integer id) {
        super("Procedimiento no encontrado: " + id);
    }
}
