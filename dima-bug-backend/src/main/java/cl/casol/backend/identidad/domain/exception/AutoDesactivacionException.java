package cl.casol.backend.identidad.domain.exception;

/**
 * Error del módulo de usuarios y acceso: auto desactivación.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class AutoDesactivacionException extends RuntimeException {
    public AutoDesactivacionException() {
        super("No puede desactivar su propio usuario");
    }
}
