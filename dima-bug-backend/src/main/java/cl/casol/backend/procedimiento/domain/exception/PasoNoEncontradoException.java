package cl.casol.backend.procedimiento.domain.exception;

public class PasoNoEncontradoException extends RuntimeException {
    public PasoNoEncontradoException(Integer id) { super("Paso no encontrado: " + id); }
}
