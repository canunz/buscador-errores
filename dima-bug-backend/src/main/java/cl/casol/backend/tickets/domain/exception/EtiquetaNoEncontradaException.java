package cl.casol.backend.tickets.domain.exception;

public class EtiquetaNoEncontradaException extends RuntimeException {
    public EtiquetaNoEncontradaException(Long id) {
        super("No se encontró la etiqueta " + id);
    }
}