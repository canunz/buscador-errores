package cl.casol.backend.identidad.domain.exception;

public class AutoEliminacionException extends RuntimeException {
    public AutoEliminacionException() {
        super("No puedes eliminar tu propia cuenta.");
    }
}
