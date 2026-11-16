package cl.casol.backend.ejecucion.domain.exception;

public class EjecucionNoEncontradaException extends RuntimeException {
    public EjecucionNoEncontradaException(Integer id) { super("Ejecucion no encontrada: " + id); }
}
