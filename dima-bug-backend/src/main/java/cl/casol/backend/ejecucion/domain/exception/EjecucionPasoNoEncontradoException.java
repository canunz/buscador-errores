package cl.casol.backend.ejecucion.domain.exception;

public class EjecucionPasoNoEncontradoException extends RuntimeException {
    public EjecucionPasoNoEncontradoException(Integer id) { super("Paso de ejecucion no encontrado: " + id); }
}
