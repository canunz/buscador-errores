package cl.casol.backend.procedimiento.domain.exception;

/**
 * Error del módulo de procedimientos: orden paso duplicado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class OrdenPasoDuplicadoException extends RuntimeException {
    public OrdenPasoDuplicadoException(Integer procedimientoId, Integer orden) {
        super("Ya existe un paso con orden " + orden + " en el procedimiento " + procedimientoId);
    }
}
