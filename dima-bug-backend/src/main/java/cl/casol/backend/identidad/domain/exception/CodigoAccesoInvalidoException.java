package cl.casol.backend.identidad.domain.exception;

public class CodigoAccesoInvalidoException extends RuntimeException {

    public CodigoAccesoInvalidoException() {
        super("El código no es válido o ya expiró. Solicite uno nuevo.");
    }
}
