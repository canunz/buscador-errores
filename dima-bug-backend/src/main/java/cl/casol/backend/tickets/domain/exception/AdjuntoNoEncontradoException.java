package cl.casol.backend.tickets.domain.exception;

public class AdjuntoNoEncontradoException extends RuntimeException {
    public AdjuntoNoEncontradoException(Long id) {
        super("No se encontró el archivo " + id);
    }
}
