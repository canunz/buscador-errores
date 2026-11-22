package cl.casol.backend.identidad.domain.exception;
/**
 * Error del módulo de usuarios y acceso: organización inválida.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class OrganizacionInvalidaException extends RuntimeException {
    public OrganizacionInvalidaException(String mensaje) { super(mensaje); }
}
