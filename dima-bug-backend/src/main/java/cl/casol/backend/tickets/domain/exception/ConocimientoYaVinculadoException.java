package cl.casol.backend.tickets.domain.exception;

public class ConocimientoYaVinculadoException extends RuntimeException {
    public ConocimientoYaVinculadoException() {
        super("El ticket ya tiene un conocimiento vinculado");
    }
}