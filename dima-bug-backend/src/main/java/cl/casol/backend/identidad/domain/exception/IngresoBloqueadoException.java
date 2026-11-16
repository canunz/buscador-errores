package cl.casol.backend.identidad.domain.exception;

public class IngresoBloqueadoException extends RuntimeException {

    private final long segundosRestantes;

    public IngresoBloqueadoException(long segundosRestantes) {
        super(mensaje(segundosRestantes));
        this.segundosRestantes = Math.max(1, segundosRestantes);
    }

    public long getSegundosRestantes() {
        return segundosRestantes;
    }

    private static String mensaje(long segundos) {
        long t = Math.max(1, segundos);
        if (t < 60) {
            return "Demasiados intentos fallidos. Espere " + t + " segundos para volver a intentar.";
        }
        long minutos = t / 60;
        long resto = t % 60;
        if (resto == 0) {
            return "Demasiados intentos fallidos. Espere " + minutos
                    + (minutos == 1 ? " minuto" : " minutos") + " para volver a intentar.";
        }
        return "Demasiados intentos fallidos. Espere " + minutos + " min " + resto + " s para volver a intentar.";
    }
}
