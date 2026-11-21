package cl.casol.backend.conocimiento.domain.exception;

/**
 * Error del módulo de conocimiento: material apoyo no encontrado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class MaterialApoyoNoEncontradoException extends RuntimeException {
    public MaterialApoyoNoEncontradoException(Integer id) {
        super("No existe el material de apoyo con id " + id + " para el conocimiento indicado");
    }
}
