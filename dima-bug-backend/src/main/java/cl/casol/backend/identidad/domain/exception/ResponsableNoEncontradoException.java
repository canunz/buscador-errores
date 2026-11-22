package cl.casol.backend.identidad.domain.exception;

/**
 * Error del módulo de usuarios y acceso: responsable no encontrado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class ResponsableNoEncontradoException extends RuntimeException {
    public ResponsableNoEncontradoException(Integer id) {
        super("Responsable no encontrado o inactivo: " + id);
    }
}
