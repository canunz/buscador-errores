package cl.casol.backend.identidad.domain.exception;

/**
 * Error del módulo de usuarios y acceso: rol no encontrado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class RolNoEncontradoException extends RuntimeException {
    public RolNoEncontradoException() {
        super("Rol no encontrado");
    }
}
