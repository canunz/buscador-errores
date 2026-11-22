package cl.casol.backend.catalogo.domain.exception;
/**
 * Error del módulo de catálogos: catálogo inválido.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class CatalogoInvalidoException extends RuntimeException {
    public CatalogoInvalidoException(String mensaje) { super(mensaje); }
}
