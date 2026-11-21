package cl.casol.backend.identidad.domain.exception;

/**
 * Error del módulo de usuarios y acceso: usuario inactivo.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class UsuarioInactivoException extends RuntimeException {

    public UsuarioInactivoException() {
        super("Usuario inactivo");
    }
}