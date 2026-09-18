package cl.casol.backend.conocimiento.domain.exception;

public class PruebaYaAsociadaException extends RuntimeException {
    public PruebaYaAsociadaException(Integer conocimientoId, Integer pruebaId) {
        super("La prueba " + pruebaId + " ya está asociada al conocimiento " + conocimientoId);
    }
}
