package cl.casol.backend.conocimiento.domain.exception;

public class AsignacionSolucionNoEncontradaException extends RuntimeException {
    public AsignacionSolucionNoEncontradaException(Integer id) {
        super("No existe la asignación de solución con id " + id);
    }
}
