package cl.casol.backend.procedimiento.domain.exception;

public class ProcedimientoNoEncontradoException extends RuntimeException {
    public ProcedimientoNoEncontradoException(Integer id) {
        super("Procedimiento no encontrado: " + id);
    }
}
