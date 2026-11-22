package cl.casol.backend.conocimiento.domain.exception;
/**
 * Error del módulo de conocimiento: copia solución inválida.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class CopiaSolucionInvalidaException extends RuntimeException {
    public CopiaSolucionInvalidaException(String mensaje) { super(mensaje); }
}
