package cl.casol.backend.seguimiento.domain.exception;

public class EstadoConocimientoNoEvaluableException extends RuntimeException {
    public EstadoConocimientoNoEvaluableException() {
        super("Solo se pueden registrar resultados en conocimientos PUBLICADOS");
    }
}
