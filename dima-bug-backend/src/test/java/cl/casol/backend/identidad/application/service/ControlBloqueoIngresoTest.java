package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.domain.exception.IngresoBloqueadoException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ControlBloqueoIngresoTest {

    @Test
    void tresFallosBloqueanTreintaSegundosYElSiguienteDuplica() {
        MutableClock reloj = new MutableClock(Instant.parse("2026-11-16T12:00:00Z"));
        ControlBloqueoIngreso control = new ControlBloqueoIngreso(reloj);

        assertEquals(0, control.registrarFallo("a@b.cl"));
        assertEquals(0, control.registrarFallo("a@b.cl"));
        assertEquals(30, control.registrarFallo("a@b.cl"));
        assertThrows(IngresoBloqueadoException.class, () -> control.exigirDesbloqueado("a@b.cl"));

        reloj.avanzar(Duration.ofSeconds(31));
        control.exigirDesbloqueado("a@b.cl");
        assertEquals(60, control.registrarFallo("a@b.cl"));
        assertThrows(IngresoBloqueadoException.class, () -> control.exigirDesbloqueado("a@b.cl"));
    }

    private static final class MutableClock extends Clock {
        private Instant ahora;
        private final ZoneOffset zona = ZoneOffset.UTC;

        MutableClock(Instant ahora) {
            this.ahora = ahora;
        }

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public ZoneOffset getZone() {
            return zona;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
