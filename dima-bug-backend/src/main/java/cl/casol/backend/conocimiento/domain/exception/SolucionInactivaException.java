package cl.casol.backend.conocimiento.domain.exception;
/**
 * Error del módulo de conocimiento: solución inactiva.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class SolucionInactivaException extends RuntimeException {
    public SolucionInactivaException() { super("RN-16: una solución inactiva no admite edición, asignaciones ni nuevos resultados"); }
}
