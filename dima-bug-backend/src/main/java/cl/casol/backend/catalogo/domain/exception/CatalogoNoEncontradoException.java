package cl.casol.backend.catalogo.domain.exception;

/**
 * Error del módulo de catálogos: catálogo no encontrado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class CatalogoNoEncontradoException extends RuntimeException {
    public CatalogoNoEncontradoException(String tipo, Integer id) {
        super(tipo + " no encontrado con id " + id);
    }
}
