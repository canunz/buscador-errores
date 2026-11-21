package cl.casol.backend.identidad.domain.exception;
/**
 * Error del módulo de usuarios y acceso: contacto departamento no encontrado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class ContactoDepartamentoNoEncontradoException extends RuntimeException {
    public ContactoDepartamentoNoEncontradoException(String mensaje) { super(mensaje); }
}
