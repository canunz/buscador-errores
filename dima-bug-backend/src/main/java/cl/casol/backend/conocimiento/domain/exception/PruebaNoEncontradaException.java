package cl.casol.backend.conocimiento.domain.exception;

/**
 * Error del módulo de conocimiento: prueba no encontrada.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class PruebaNoEncontradaException extends RuntimeException {
    public PruebaNoEncontradaException(Integer id) {
        super("No existe una prueba activa con id " + id);
    }
}
