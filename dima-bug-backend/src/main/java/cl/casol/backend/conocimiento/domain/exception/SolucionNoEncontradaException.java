package cl.casol.backend.conocimiento.domain.exception;

/**
 * Error del módulo de conocimiento: solución no encontrada.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class SolucionNoEncontradaException extends RuntimeException {
    public SolucionNoEncontradaException(Integer id) {
        super("No existe la solución con id " + id + " para el conocimiento indicado");
    }
}
