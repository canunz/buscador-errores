package cl.casol.backend.identidad.domain.exception;
/**
 * Error del módulo de usuarios y acceso: organización conflicto.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class OrganizacionConflictoException extends RuntimeException {
    public OrganizacionConflictoException(String mensaje) { super(mensaje); }
}
