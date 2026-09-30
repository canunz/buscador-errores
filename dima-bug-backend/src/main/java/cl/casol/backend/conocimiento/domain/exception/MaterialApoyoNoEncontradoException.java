package cl.casol.backend.conocimiento.domain.exception;

public class MaterialApoyoNoEncontradoException extends RuntimeException {
    public MaterialApoyoNoEncontradoException(Integer id) {
        super("No existe el material de apoyo con id " + id + " para el conocimiento indicado");
    }
}
