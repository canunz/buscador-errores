package cl.casol.backend.catalogo.domain.exception;

public class CatalogoNoEncontradoException extends RuntimeException {
    public CatalogoNoEncontradoException(String tipo, Integer id) {
        super(tipo + " no encontrado con id " + id);
    }
}
