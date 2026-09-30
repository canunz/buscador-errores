package cl.casol.backend.identidad.domain.exception;

public class DepartamentoNoEncontradoException extends RuntimeException {
    public DepartamentoNoEncontradoException(Integer id) {
        super("Departamento no encontrado o inactivo: " + id);
    }
}
