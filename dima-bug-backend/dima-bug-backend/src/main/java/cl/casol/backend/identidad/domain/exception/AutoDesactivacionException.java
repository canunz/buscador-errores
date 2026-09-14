package cl.casol.backend.identidad.domain.exception;

public class AutoDesactivacionException extends RuntimeException {
    public AutoDesactivacionException() {
        super("No puede desactivar su propio usuario");
    }
}
