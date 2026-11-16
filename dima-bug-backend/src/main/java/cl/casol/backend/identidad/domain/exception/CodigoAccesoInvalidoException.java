package cl.casol.backend.identidad.domain.exception;

public class CodigoAccesoInvalidoException extends RuntimeException {

    public CodigoAccesoInvalidoException() {
        super("El código no es válido o ya expiró. Solicite uno nuevo.");
    }

    public CodigoAccesoInvalidoException(int intentosRestantes) {
        super(intentosRestantes <= 0
                ? "El código no es válido o ya expiró. Solicite uno nuevo."
                : "El código no es válido. Le quedan " + intentosRestantes
                        + (intentosRestantes == 1 ? " intento" : " intentos")
                        + " antes de un bloqueo temporal.");
    }
}
