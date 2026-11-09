package cl.casol.backend.identidad.domain.exception;

public class RecuperacionNoDisponibleException extends RuntimeException {

    public RecuperacionNoDisponibleException() {
        super("Esta cuenta ingresa con código de acceso, no con contraseña.");
    }
}
