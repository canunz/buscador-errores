package cl.casol.backend.identidad.domain.exception;

//Paso V: Manejo de errores
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Credenciales inválidas");
    }
}