package cl.casol.backend.identidad.domain.exception;
/**
 * Error del módulo de usuarios y acceso: último administrador.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class UltimoAdministradorException extends RuntimeException {
    public UltimoAdministradorException() { super("Debe conservar al menos un administrador activo"); }
}
