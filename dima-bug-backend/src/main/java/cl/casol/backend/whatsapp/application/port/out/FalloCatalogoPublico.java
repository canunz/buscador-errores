package cl.casol.backend.whatsapp.application.port.out;

import java.time.Duration;

/** Fallo técnico, nunca equivalente a catálogo vacío; sin URL, cuerpo ni credenciales. */
public final class FalloCatalogoPublico extends RuntimeException {
    private final boolean reintentable;
    private final Duration esperaMinima;
    public FalloCatalogoPublico(boolean reintentable, Duration esperaMinima) {
        super("CATALOGO_PUBLICO_NO_DISPONIBLE");
        if (esperaMinima == null || esperaMinima.isNegative() || esperaMinima.compareTo(Duration.ofDays(1)) > 0)
            throw new IllegalArgumentException("Espera de catálogo inválida");
        this.reintentable=reintentable; this.esperaMinima=esperaMinima;
    }
    public boolean reintentable(){return reintentable;}
    public Duration esperaMinima(){return esperaMinima;}
}
