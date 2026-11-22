package cl.casol.backend.conocimiento.domain.exception;

/**
 * Error del módulo de conocimiento: asociación prueba no encontrada.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class AsociacionPruebaNoEncontradaException extends RuntimeException {
    public AsociacionPruebaNoEncontradaException(Integer conocimientoId, Integer pruebaId) {
        super("No existe la asociación entre el conocimiento " + conocimientoId + " y la prueba " + pruebaId);
    }
}
