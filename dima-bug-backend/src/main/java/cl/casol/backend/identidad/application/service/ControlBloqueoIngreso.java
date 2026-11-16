package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.domain.exception.IngresoBloqueadoException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tras 3 códigos incorrectos se bloquea 30 segundos.
 * Cada fallo siguiente (cuando el bloqueo ya venció) duplica la espera: 60, 120, 240…
 */
@Component
public class ControlBloqueoIngreso {

    public static final int INTENTOS_ANTES_DE_BLOQUEO = 3;
    public static final int SEGUNDOS_INICIAL = 30;
    public static final int SEGUNDOS_MAXIMO = 15 * 60;

    private final Clock clock;
    private final ConcurrentHashMap<String, Estado> porCorreo = new ConcurrentHashMap<>();

    public ControlBloqueoIngreso() {
        this(Clock.systemDefaultZone());
    }

    public ControlBloqueoIngreso(Clock clock) {
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public void exigirDesbloqueado(String correo) {
        Estado estado = porCorreo.get(clave(correo));
        if (estado == null) {
            return;
        }
        Instant ahora = Instant.now(clock);
        if (estado.hasta != null && ahora.isBefore(estado.hasta)) {
            throw new IngresoBloqueadoException(Math.max(1, Duration.between(ahora, estado.hasta).getSeconds()));
        }
    }

    /** @return segundos de bloqueo si este fallo acaba de cerrar el ingreso; 0 si aún puede intentar */
    public int registrarFallo(String correo) {
        String key = clave(correo);
        Instant ahora = Instant.now(clock);
        Estado siguiente = porCorreo.compute(key, (k, actual) -> {
            Estado base = actual == null ? new Estado() : actual;
            if (base.hasta != null && ahora.isBefore(base.hasta)) {
                return base;
            }
            int fallos = base.fallosSeguidos + 1;
            int rondas = base.rondasBloqueo;
            Instant hasta = null;
            boolean bloquear = rondas > 0 || fallos >= INTENTOS_ANTES_DE_BLOQUEO;
            if (bloquear) {
                int segundos = Math.min(SEGUNDOS_MAXIMO, SEGUNDOS_INICIAL * (1 << Math.min(rondas, 10)));
                hasta = ahora.plusSeconds(segundos);
                rondas = rondas + 1;
                fallos = 0;
            }
            return new Estado(fallos, rondas, hasta);
        });
        if (siguiente.hasta != null && ahora.isBefore(siguiente.hasta) && siguiente.fallosSeguidos == 0) {
            return (int) Math.max(1, Duration.between(ahora, siguiente.hasta).getSeconds());
        }
        return 0;
    }

    public int intentosRestantes(String correo) {
        Estado estado = porCorreo.get(clave(correo));
        if (estado == null || estado.rondasBloqueo > 0) {
            return estado != null && estado.rondasBloqueo > 0 ? 0 : INTENTOS_ANTES_DE_BLOQUEO;
        }
        return Math.max(0, INTENTOS_ANTES_DE_BLOQUEO - estado.fallosSeguidos);
    }

    public void limpiar(String correo) {
        porCorreo.remove(clave(correo));
    }

    private static String clave(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase();
    }

    private static final class Estado {
        final int fallosSeguidos;
        final int rondasBloqueo;
        final Instant hasta;

        Estado() {
            this(0, 0, null);
        }

        Estado(int fallosSeguidos, int rondasBloqueo, Instant hasta) {
            this.fallosSeguidos = fallosSeguidos;
            this.rondasBloqueo = rondasBloqueo;
            this.hasta = hasta;
        }
    }
}
