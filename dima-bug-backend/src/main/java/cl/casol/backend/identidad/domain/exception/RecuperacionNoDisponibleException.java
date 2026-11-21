package cl.casol.backend.identidad.domain.exception;

/**
 * Error del módulo de usuarios y acceso: recuperación no disponible.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class RecuperacionNoDisponibleException extends RuntimeException {

    public RecuperacionNoDisponibleException() {
        super("Esta cuenta ingresa con código de acceso, no con contraseña.");
    }
}
