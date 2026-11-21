package cl.casol.backend.tickets.domain.exception;

/**
 * Error del módulo de tickets: adjunto no encontrado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class AdjuntoNoEncontradoException extends RuntimeException {
    public AdjuntoNoEncontradoException(Long id) {
        super("No se encontró el archivo " + id);
    }
}
