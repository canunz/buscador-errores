package cl.casol.backend.conocimiento.domain.exception;
/**
 * Error del módulo de conocimiento: solución duplicada.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class SolucionDuplicadaException extends RuntimeException {
    public SolucionDuplicadaException() { super("Ya existe una solución con el mismo título, descripción y tipo en el conocimiento destino, incluso si está inactiva"); }
}
