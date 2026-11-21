package cl.casol.backend.tickets.domain.exception;

/**
 * Error del módulo de tickets: conocimiento ya vinculado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class ConocimientoYaVinculadoException extends RuntimeException {
    public ConocimientoYaVinculadoException() {
        super("El ticket ya tiene un conocimiento vinculado");
    }
}