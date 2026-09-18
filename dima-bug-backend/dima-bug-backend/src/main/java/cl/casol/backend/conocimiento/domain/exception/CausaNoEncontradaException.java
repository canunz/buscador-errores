package cl.casol.backend.conocimiento.domain.exception;

public class CausaNoEncontradaException extends RuntimeException {
    public CausaNoEncontradaException(Integer id) {
        super("No existe la causa con id " + id + " para el conocimiento indicado");
    }
}
