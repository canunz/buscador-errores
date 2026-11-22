package cl.casol.backend.tickets.domain.exception;

/**
 * Error del módulo de tickets: etiqueta no encontrada.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class EtiquetaNoEncontradaException extends RuntimeException {
    public EtiquetaNoEncontradaException(Long id) {
        super("No se encontró la etiqueta " + id);
    }
}