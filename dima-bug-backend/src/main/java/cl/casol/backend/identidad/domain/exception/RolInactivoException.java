package cl.casol.backend.identidad.domain.exception;

/**
 * Error del módulo de usuarios y acceso: rol inactivo.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class RolInactivoException extends RuntimeException {
    public RolInactivoException() {
        super("El rol está inactivo");
    }
}
