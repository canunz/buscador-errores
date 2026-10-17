package cl.casol.backend.tickets.domain.exception;

public class EtiquetaDuplicadaException extends RuntimeException {
    public EtiquetaDuplicadaException(String nombre) {
        super("Ya existe una etiqueta llamada \"" + nombre + "\"");
    }
}