package cl.casol.backend.conocimiento.domain.exception;

public class ConocimientoNoEncontradoException extends RuntimeException {
    public ConocimientoNoEncontradoException(Integer id) {
        super("No existe el conocimiento con id " + id);
    }
}
