package cl.casol.backend.identidad.domain.exception;

/**
 * Error del módulo de usuarios y acceso: correo no registrado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class CorreoNoRegistradoException extends RuntimeException {

    public CorreoNoRegistradoException() {
        super("Ese correo no tiene cuenta en DimaBug. Solo los correos @dimarsa.cl pueden registrarse solos; con otro correo, pida al administrador que le cree la cuenta.");
    }
}
