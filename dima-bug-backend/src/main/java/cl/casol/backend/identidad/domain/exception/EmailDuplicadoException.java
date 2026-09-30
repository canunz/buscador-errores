package cl.casol.backend.identidad.domain.exception;

public class EmailDuplicadoException extends RuntimeException {
    public EmailDuplicadoException() {
        super("El email ya está registrado");
    }
}
