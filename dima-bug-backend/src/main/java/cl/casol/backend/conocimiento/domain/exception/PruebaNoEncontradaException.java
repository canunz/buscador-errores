package cl.casol.backend.conocimiento.domain.exception;

public class PruebaNoEncontradaException extends RuntimeException {
    public PruebaNoEncontradaException(Integer id) {
        super("No existe una prueba activa con id " + id);
    }
}
