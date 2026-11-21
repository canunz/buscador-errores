package cl.casol.backend.identidad.domain.exception;

/**
 * Se lanza cuando el usuario se equivocó demasiadas veces con su código
 * y tiene que esperar antes de volver a intentarlo.
 *
 * Hereda de CodigoAccesoInvalidoException porque, para el resto del sistema,
 * sigue siendo un ingreso rechazado. La diferencia es que además informa
 * cuántos segundos debe esperar el usuario.
 */
public class IngresoBloqueadoException extends CodigoAccesoInvalidoException {

    private final long segundosRestantes;

    public IngresoBloqueadoException(long segundosRestantes) {
        super(mensaje(segundosRestantes));
        this.segundosRestantes = segundosRestantes;
    }

    public long getSegundosRestantes() {
        return segundosRestantes;
    }

    /** Arma el mensaje en segundos o en minutos, según cuánto falte. */
    private static String mensaje(long segundos) {
        if (segundos < 60) {
            return "Demasiados intentos fallidos. Espere " + segundos + " segundos para volver a intentarlo.";
        }
        long minutos = (segundos + 59) / 60;
        return "Demasiados intentos fallidos. Espere " + minutos
                + (minutos == 1 ? " minuto" : " minutos") + " para volver a intentarlo.";
    }
}
