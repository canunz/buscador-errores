package cl.casol.backend.conocimiento.domain.exception;

/**
 * Error del módulo de conocimiento: síntoma no encontrado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class SintomaNoEncontradoException extends RuntimeException {
    public SintomaNoEncontradoException(Integer id) {
        super("No existe el síntoma con id " + id + " para el conocimiento indicado");
    }
}
