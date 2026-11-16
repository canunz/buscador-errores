package cl.casol.backend.identidad.domain.exception;

public class EnvioCodigoAccesoException extends RuntimeException {

    public EnvioCodigoAccesoException(Throwable causa) {
        super("No fue posible enviar el código a su correo. Intente nuevamente en unos minutos.", causa);
    }
}
