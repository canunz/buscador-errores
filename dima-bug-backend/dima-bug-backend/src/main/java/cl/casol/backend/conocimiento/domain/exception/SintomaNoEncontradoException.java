package cl.casol.backend.conocimiento.domain.exception;

public class SintomaNoEncontradoException extends RuntimeException {
    public SintomaNoEncontradoException(Integer id) {
        super("No existe el síntoma con id " + id + " para el conocimiento indicado");
    }
}
