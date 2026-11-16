package cl.casol.backend.identidad.domain.exception;

public class RolInactivoException extends RuntimeException {
    public RolInactivoException() {
        super("El rol está inactivo");
    }
}
