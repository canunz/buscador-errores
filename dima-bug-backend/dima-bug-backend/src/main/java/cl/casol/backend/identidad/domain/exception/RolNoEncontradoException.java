package cl.casol.backend.identidad.domain.exception;

public class RolNoEncontradoException extends RuntimeException {
    public RolNoEncontradoException() {
        super("Rol no encontrado");
    }
}
