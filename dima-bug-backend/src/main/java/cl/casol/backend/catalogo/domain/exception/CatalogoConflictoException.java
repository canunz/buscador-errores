package cl.casol.backend.catalogo.domain.exception;
/**
 * Error del módulo de catálogos: catálogo conflicto.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class CatalogoConflictoException extends RuntimeException {
    public CatalogoConflictoException(String mensaje) { super(mensaje); }
}
