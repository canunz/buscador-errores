package cl.casol.backend.conocimiento.domain.exception;
/**
 * Error del módulo de conocimiento: última solución activa.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class UltimaSolucionActivaException extends RuntimeException {
    public UltimaSolucionActivaException() { super("RN-08: un conocimiento PUBLICADO debe conservar al menos una solución activa"); }
}
