package cl.casol.backend.identidad.domain.exception;

/**
 * Error del módulo de usuarios y acceso: correo no registrado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class CorreoNoRegistradoException extends RuntimeException {

    public CorreoNoRegistradoException() {
        super("Este correo no está registrado en DimaBug. Inténtelo con un correo que exista.");
    }
}
