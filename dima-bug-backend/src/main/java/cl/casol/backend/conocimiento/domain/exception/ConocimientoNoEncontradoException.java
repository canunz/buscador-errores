package cl.casol.backend.conocimiento.domain.exception;

/**
 * Error del módulo de conocimiento: conocimiento no encontrado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class ConocimientoNoEncontradoException extends RuntimeException {
    public ConocimientoNoEncontradoException(Integer id) {
        super("No existe el conocimiento con id " + id);
    }
}
