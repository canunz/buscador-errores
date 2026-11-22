package cl.casol.backend.shared.domain;
/**
 * Error del módulo de uso común: destinatario inválido.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class DestinatarioInvalidoException extends RuntimeException {
    public DestinatarioInvalidoException(String mensaje){super(mensaje);}
}
