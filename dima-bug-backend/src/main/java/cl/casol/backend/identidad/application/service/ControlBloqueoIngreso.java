package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.domain.exception.IngresoBloqueadoException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lleva la cuenta de los códigos mal ingresados por cada usuario y decide
 * cuándo bloquear el ingreso y por cuánto tiempo.
 *
 * La regla es la siguiente:
 *  - El usuario tiene 3 intentos para escribir bien su código.
 *  - Si se equivoca las 3 veces, queda bloqueado 30 segundos.
 *  - Si pasado ese tiempo se vuelve a equivocar, el bloqueo ya es por minutos:
 *    primero 2 minutos, después 5 y desde ahí 15 minutos cada vez.
 *  - Cuando por fin entra bien, todo vuelve a cero.
 *  - Si pasa una hora sin errores, también se parte de cero.
 *
 * Los contadores se guardan en la memoria del servidor, uno por usuario.
 * Si el backend se reinicia, se pierden y el usuario parte limpio.
 */
public class ControlBloqueoIngreso {

    /** Intentos que tiene el usuario antes del primer bloqueo. */
    public static final int INTENTOS_PERMITIDOS = 3;

    /** Duración de cada bloqueo, en orden. Al llegar al último, se repite ese. */
    private static final Duration[] DURACION_BLOQUEOS = {
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15)
    };

    /** Si pasa este tiempo sin errores, se olvida lo que el usuario había fallado antes. */
    private static final Duration OLVIDAR_DESPUES_DE = Duration.ofHours(1);

    private final Clock clock;
    private final Map<Integer, Estado> estadosPorUsuario = new ConcurrentHashMap<>();

    public ControlBloqueoIngreso(Clock clock) {
        this.clock = clock;
    }

    /**
     * Revisa si el usuario está bloqueado en este momento.
     * Si lo está, corta el ingreso y le indica cuántos segundos le faltan.
     */
    public void exigirDesbloqueado(Integer usuarioId) {
        Estado estado = estadosPorUsuario.get(usuarioId);
        if (estado == null) {
            return;
        }

        long segundos = estado.segundosRestantes(ahora());
        if (segundos > 0) {
            throw new IngresoBloqueadoException(segundos);
        }
    }

    /**
     * Anota que el usuario escribió mal su código.
     *
     * @return los segundos de bloqueo si este error activó uno, o 0 si todavía puede seguir intentando
     */
    public long registrarFallo(Integer usuarioId) {
        LocalDateTime ahora = ahora();

        Estado nuevoEstado = estadosPorUsuario.compute(usuarioId, (id, actual) -> {
            // Si no tenía errores, o los tenía pero hace mucho, se parte de cero.
            Estado base = (actual == null || actual.sePuedeOlvidar(ahora)) ? Estado.inicial() : actual;
            return base.conUnFalloMas(ahora);
        });

        return nuevoEstado.segundosRestantes(ahora);
    }

    /** El usuario entró bien, así que se borra su historial de errores. */
    public void limpiar(Integer usuarioId) {
        estadosPorUsuario.remove(usuarioId);
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock);
    }

    /**
     * Situación de un usuario.
     *
     * @param fallos         errores seguidos desde el último bloqueo (o desde el inicio)
     * @param bloqueos       cuántas veces ya ha sido bloqueado
     * @param bloqueadoHasta momento en que termina el bloqueo actual, o null si no tiene
     * @param ultimoFallo    momento del último error
     */
    private record Estado(int fallos, int bloqueos, LocalDateTime bloqueadoHasta, LocalDateTime ultimoFallo) {

        static Estado inicial() {
            return new Estado(0, 0, null, null);
        }

        // Devuelve con un fallo mas.
        Estado conUnFalloMas(LocalDateTime ahora) {
            int fallosAcumulados = fallos + 1;

            // La primera vez tiene 3 intentos. Si ya estuvo bloqueado antes,
            // basta un error más para volver a bloquearlo, ahora por más tiempo.
            boolean tocaBloquear = bloqueos > 0 || fallosAcumulados >= INTENTOS_PERMITIDOS;
            if (!tocaBloquear) {
                return new Estado(fallosAcumulados, bloqueos, bloqueadoHasta, ahora);
            }

            Duration duracion = DURACION_BLOQUEOS[Math.min(bloqueos, DURACION_BLOQUEOS.length - 1)];
            return new Estado(0, bloqueos + 1, ahora.plus(duracion), ahora);
        }

        // Devuelve segundos restantes.
        long segundosRestantes(LocalDateTime ahora) {
            if (bloqueadoHasta == null || !ahora.isBefore(bloqueadoHasta)) {
                return 0;
            }
            // Se redondea hacia arriba para no mostrar "0 segundos" cuando aún falta un poco.
            long milisegundos = Duration.between(ahora, bloqueadoHasta).toMillis();
            return Math.max(1, (milisegundos + 999) / 1000);
        }

        // Indica si se puede olvidar.
        boolean sePuedeOlvidar(LocalDateTime ahora) {
            return ultimoFallo != null
                    && segundosRestantes(ahora) == 0
                    && Duration.between(ultimoFallo, ahora).compareTo(OLVIDAR_DESPUES_DE) >= 0;
        }
    }
}
