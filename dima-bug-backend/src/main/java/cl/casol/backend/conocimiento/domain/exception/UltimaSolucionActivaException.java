package cl.casol.backend.conocimiento.domain.exception;
public class UltimaSolucionActivaException extends RuntimeException {
    public UltimaSolucionActivaException() { super("RN-08: un conocimiento PUBLICADO debe conservar al menos una solución activa"); }
}
