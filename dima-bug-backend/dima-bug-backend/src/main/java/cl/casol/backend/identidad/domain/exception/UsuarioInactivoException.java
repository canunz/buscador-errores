package cl.casol.backend.identidad.domain.exception;

public class UsuarioInactivoException extends RuntimeException {

    public UsuarioInactivoException() {
        super("Usuario inactivo");
    }
}