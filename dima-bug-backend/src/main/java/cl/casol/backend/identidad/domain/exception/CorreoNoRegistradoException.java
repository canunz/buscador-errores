package cl.casol.backend.identidad.domain.exception;

public class CorreoNoRegistradoException extends RuntimeException {

    public CorreoNoRegistradoException() {
        super("Este correo no está registrado en DimaBug. Inténtelo con un correo que exista.");
    }
}
