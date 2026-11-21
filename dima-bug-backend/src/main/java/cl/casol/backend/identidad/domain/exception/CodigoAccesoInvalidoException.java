package cl.casol.backend.identidad.domain.exception;

/**
 * Error del módulo de usuarios y acceso: código acceso inválido.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class CodigoAccesoInvalidoException extends RuntimeException {

    public CodigoAccesoInvalidoException() {
        super("El código no es válido o ya expiró. Solicite uno nuevo.");
    }

    /** Permite que el bloqueo por intentos fallidos use su propio mensaje. */
    protected CodigoAccesoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
