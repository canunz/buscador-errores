package cl.casol.backend.identidad.domain.exception;

/**
 * Error del módulo de usuarios y acceso: email duplicado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class EmailDuplicadoException extends RuntimeException {
    public EmailDuplicadoException() {
        super("El email ya está registrado");
    }
}
