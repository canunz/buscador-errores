package cl.casol.backend.identidad.domain.exception;

/**
 * Error del módulo de usuarios y acceso: reenvío código anticipado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class ReenvioCodigoAnticipadoException extends RuntimeException {

    private final long segundosRestantes;

    public ReenvioCodigoAnticipadoException(long segundosRestantes) {
        super("Espere " + segundosRestantes + " segundos antes de solicitar un nuevo código.");
        this.segundosRestantes = segundosRestantes;
    }

    public long getSegundosRestantes() {
        return segundosRestantes;
    }
}
