package cl.casol.backend.conocimiento.domain.exception;

/**
 * Error del módulo de conocimiento: causa no encontrada.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class CausaNoEncontradaException extends RuntimeException {
    public CausaNoEncontradaException(Integer id) {
        super("No existe la causa con id " + id + " para el conocimiento indicado");
    }
}
