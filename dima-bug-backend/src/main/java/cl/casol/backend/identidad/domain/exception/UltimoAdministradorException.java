package cl.casol.backend.identidad.domain.exception;
public class UltimoAdministradorException extends RuntimeException {
    public UltimoAdministradorException() { super("Debe conservar al menos un administrador activo"); }
}
