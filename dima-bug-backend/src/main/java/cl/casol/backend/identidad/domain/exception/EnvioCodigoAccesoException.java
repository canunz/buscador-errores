package cl.casol.backend.identidad.domain.exception;

/**
 * Error del módulo de usuarios y acceso: envío código acceso.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class EnvioCodigoAccesoException extends RuntimeException {

    public EnvioCodigoAccesoException(Throwable causa) {
        super("No fue posible enviar el código a su correo. Intente nuevamente en unos minutos.", causa);
    }
}
