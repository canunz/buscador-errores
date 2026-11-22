package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.domain.exception.IngresoBloqueadoException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Revisa la regla de bloqueo: 3 intentos, después 30 segundos
 * y luego bloqueos en minutos que van subiendo.
 */
class ControlBloqueoIngresoTest {

    private static final Integer USUARIO = 1;

    /** Reloj que se puede adelantar a mano para simular el paso del tiempo. */
    private final RelojManual reloj = new RelojManual(Instant.parse("2026-11-17T12:00:00Z"));
    private final ControlBloqueoIngreso control = new ControlBloqueoIngreso(reloj);

    @Test
    void losDosPrimerosErroresNoBloquean() {
        assertEquals(0, control.registrarFallo(USUARIO));
        assertEquals(0, control.registrarFallo(USUARIO));
        assertDoesNotThrow(() -> control.exigirDesbloqueado(USUARIO));
    }

    @Test
    void alTercerErrorQuedaBloqueadoTreintaSegundos() {
        control.registrarFallo(USUARIO);
        control.registrarFallo(USUARIO);

        assertEquals(30, control.registrarFallo(USUARIO));
        IngresoBloqueadoException bloqueo =
                assertThrows(IngresoBloqueadoException.class, () -> control.exigirDesbloqueado(USUARIO));
        assertEquals(30, bloqueo.getSegundosRestantes());

        reloj.avanzar(Duration.ofSeconds(30));
        assertDoesNotThrow(() -> control.exigirDesbloqueado(USUARIO));
    }

    @Test
    void despuesDelPrimerBloqueoCadaErrorBloqueaPorMinutos() {
        tresErrores();
        reloj.avanzar(Duration.ofSeconds(30));

        // Un solo error más ya bloquea, ahora por 2 minutos.
        assertEquals(120, control.registrarFallo(USUARIO));
        reloj.avanzar(Duration.ofMinutes(2));

        assertEquals(5 * 60, control.registrarFallo(USUARIO));
        reloj.avanzar(Duration.ofMinutes(5));

        assertEquals(15 * 60, control.registrarFallo(USUARIO));
        reloj.avanzar(Duration.ofMinutes(15));

        // Desde aquí se mantiene en 15 minutos.
        assertEquals(15 * 60, control.registrarFallo(USUARIO));
    }

    @Test
    void alEntrarBienSeLimpianLosErrores() {
        tresErrores();
        control.limpiar(USUARIO);

        assertDoesNotThrow(() -> control.exigirDesbloqueado(USUARIO));
        assertEquals(0, control.registrarFallo(USUARIO));
    }

    @Test
    void despuesDeUnaHoraSinErroresParteDeCero() {
        tresErrores();
        reloj.avanzar(Duration.ofHours(1));

        // Ya no arrastra el bloqueo anterior: vuelve a tener 3 intentos.
        assertEquals(0, control.registrarFallo(USUARIO));
    }

    @Test
    void cadaUsuarioTieneSuPropioContador() {
        tresErrores();
        assertDoesNotThrow(() -> control.exigirDesbloqueado(2));
    }

    private void tresErrores() {
        control.registrarFallo(USUARIO);
        control.registrarFallo(USUARIO);
        control.registrarFallo(USUARIO);
    }

    private static final class RelojManual extends Clock {

        private Instant ahora;

        RelojManual(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration tiempo) {
            ahora = ahora.plus(tiempo);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
