package cl.casol.backend.conocimiento.domain.exception;
public class SolucionInactivaException extends RuntimeException {
    public SolucionInactivaException() { super("RN-16: una solución inactiva no admite edición, asignaciones ni nuevos resultados"); }
}
