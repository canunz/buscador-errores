package cl.casol.backend.conocimiento.domain.exception;

public class SolucionNoEncontradaException extends RuntimeException {
    public SolucionNoEncontradaException(Integer id) {
        super("No existe la solución con id " + id + " para el conocimiento indicado");
    }
}
