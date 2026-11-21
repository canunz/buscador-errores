package cl.casol.backend.tickets.domain.exception;

/**
 * Error del módulo de tickets: etiqueta duplicada.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class EtiquetaDuplicadaException extends RuntimeException {
    public EtiquetaDuplicadaException(String nombre) {
        super("Ya existe una etiqueta llamada \"" + nombre + "\"");
    }
}