package cl.casol.backend.conocimiento.domain.exception;
/**
 * Error del módulo de conocimiento: material duplicado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class MaterialDuplicadoException extends RuntimeException {
    public MaterialDuplicadoException(){super("El conocimiento destino ya contiene ese archivo o enlace");}
}
