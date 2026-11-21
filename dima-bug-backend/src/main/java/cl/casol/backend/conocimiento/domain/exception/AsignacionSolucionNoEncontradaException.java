package cl.casol.backend.conocimiento.domain.exception;

/**
 * Error del módulo de conocimiento: asignación solución no encontrada.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class AsignacionSolucionNoEncontradaException extends RuntimeException {
    public AsignacionSolucionNoEncontradaException(Integer id) {
        super("No existe la asignación de solución con id " + id);
    }
}
