package cl.casol.backend.conocimiento.domain.exception;

/**
 * Error del módulo de conocimiento: prueba ya asociada.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class PruebaYaAsociadaException extends RuntimeException {
    public PruebaYaAsociadaException(Integer conocimientoId, Integer pruebaId) {
        super("La prueba " + pruebaId + " ya está asociada al conocimiento " + conocimientoId);
    }
}
