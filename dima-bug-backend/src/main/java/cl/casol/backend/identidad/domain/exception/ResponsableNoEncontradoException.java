package cl.casol.backend.identidad.domain.exception;

public class ResponsableNoEncontradoException extends RuntimeException {
    public ResponsableNoEncontradoException(Integer id) {
        super("Responsable no encontrado o inactivo: " + id);
    }
}
