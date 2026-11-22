package cl.casol.backend.identidad.domain.exception;

/**
 * Error del módulo de usuarios y acceso: auto eliminación.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class AutoEliminacionException extends RuntimeException {
    public AutoEliminacionException() {
        super("No puedes eliminar tu propia cuenta.");
    }
}
