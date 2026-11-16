package cl.casol.backend.procedimiento.domain.exception;

public class OrdenPasoDuplicadoException extends RuntimeException {
    public OrdenPasoDuplicadoException(Integer procedimientoId, Integer orden) {
        super("Ya existe un paso con orden " + orden + " en el procedimiento " + procedimientoId);
    }
}
