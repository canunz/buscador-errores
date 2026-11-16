package cl.casol.backend.conocimiento.domain.exception;
public class TituloSolucionInvalidoException extends RuntimeException {
    public TituloSolucionInvalidoException() { super("RN-17: el título es obligatorio y admite máximo 150 caracteres"); }
}
