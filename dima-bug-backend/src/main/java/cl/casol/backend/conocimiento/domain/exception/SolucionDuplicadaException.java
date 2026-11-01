package cl.casol.backend.conocimiento.domain.exception;
public class SolucionDuplicadaException extends RuntimeException {
    public SolucionDuplicadaException() { super("Ya existe una solución con el mismo título, descripción y tipo en el conocimiento destino, incluso si está inactiva"); }
}
