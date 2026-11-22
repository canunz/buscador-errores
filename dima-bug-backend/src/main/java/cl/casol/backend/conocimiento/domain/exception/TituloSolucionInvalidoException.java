package cl.casol.backend.conocimiento.domain.exception;
/**
 * Error del módulo de conocimiento: título solución inválido.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class TituloSolucionInvalidoException extends RuntimeException {
    public TituloSolucionInvalidoException() { super("RN-17: el título es obligatorio y admite máximo 150 caracteres"); }
}
