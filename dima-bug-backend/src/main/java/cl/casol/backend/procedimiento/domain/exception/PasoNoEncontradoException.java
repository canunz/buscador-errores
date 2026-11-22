package cl.casol.backend.procedimiento.domain.exception;

/**
 * Error del módulo de procedimientos: paso no encontrado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class PasoNoEncontradoException extends RuntimeException {
    public PasoNoEncontradoException(Integer id) { super("Paso no encontrado: " + id); }
}
