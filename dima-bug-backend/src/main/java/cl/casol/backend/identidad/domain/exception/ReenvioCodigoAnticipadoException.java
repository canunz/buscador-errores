package cl.casol.backend.identidad.domain.exception;

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
