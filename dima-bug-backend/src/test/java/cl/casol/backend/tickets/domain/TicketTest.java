package cl.casol.backend.tickets.domain;

import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import cl.casol.backend.tickets.domain.exception.TransicionEstadoInvalidaException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TicketTest {
    private static final Integer SOLICITANTE = 10;
    private static final Integer TECNICO = 20;
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 17, 10, 0);

    private Ticket nuevo() {
        return Ticket.crear("TK-2026-0001", "  Impresora no imprime  ", "No sale nada", PrioridadTicket.ALTA,
                SOLICITANTE, 1, 2, 3, AHORA);
    }

    private Ticket enEstado(EstadoTicket estado) {
        return new Ticket(1L, "TK-2026-0001", "T", "D", estado, PrioridadTicket.MEDIA, SOLICITANTE,
                estado == EstadoTicket.ABIERTO ? null : TECNICO, null, null, null, null,
                estado == EstadoTicket.RESUELTO || estado == EstadoTicket.CERRADO ? "Se reinició" : null,
                AHORA, AHORA, null, null, Set.of(), List.of(), List.of());
    }

    @Nested
    class Creacion {
        @Test void quedaAbiertoConHistorialInicial() {
            Ticket ticket = nuevo();
            assertEquals(EstadoTicket.ABIERTO, ticket.getEstado());
            assertEquals("Impresora no imprime", ticket.getTitulo());
            assertEquals(1, ticket.getHistorial().size());
            assertNull(ticket.getHistorial().getFirst().estadoAnterior());
            assertEquals(EstadoTicket.ABIERTO, ticket.getHistorial().getFirst().estadoNuevo());
        }

        @Test void prioridadPorDefectoEsMedia() {
            Ticket ticket = Ticket.crear("TK-2026-0002", "T", "D", null, SOLICITANTE, null, null, null, AHORA);
            assertEquals(PrioridadTicket.MEDIA, ticket.getPrioridad());
        }

        @Test void exigeTituloYDescripcion() {
            assertThrows(TicketInvalidoException.class,
                    () -> Ticket.crear("C", " ", "D", null, SOLICITANTE, null, null, null, AHORA));
            assertThrows(TicketInvalidoException.class,
                    () -> Ticket.crear("C", "T", "", null, SOLICITANTE, null, null, null, AHORA));
            assertThrows(TicketInvalidoException.class,
                    () -> Ticket.crear("C", "x".repeat(151), "D", null, SOLICITANTE, null, null, null, AHORA));
        }

        @Test void codigoTieneFormatoAnualConCuatroDigitos() {
            assertEquals("TK-2026-0001", Ticket.generarCodigo(2026, 1));
            assertEquals("TK-2026-0123", Ticket.generarCodigo(2026, 123));
        }
    }

    @Nested
    class TransicionesValidas {
        @Test void asignarUnTicketAbiertoLoPasaAEnProgreso() {
            Ticket ticket = nuevo();
            ticket.asignar(TECNICO, TECNICO, AHORA);
            assertEquals(EstadoTicket.EN_PROGRESO, ticket.getEstado());
            assertEquals(TECNICO, ticket.getAsignadoId());
            assertEquals(2, ticket.getHistorial().size());
        }

        @Test void reasignarEnProgresoNoCambiaEstadoNiHistorial() {
            Ticket ticket = enEstado(EstadoTicket.EN_PROGRESO);
            ticket.asignar(99, 1, AHORA);
            assertEquals(99, ticket.getAsignadoId());
            assertEquals(EstadoTicket.EN_PROGRESO, ticket.getEstado());
            assertTrue(ticket.getHistorial().isEmpty());
        }

        @Test void abiertoAEnProgreso() {
            Ticket ticket = enEstado(EstadoTicket.ABIERTO);
            ticket.iniciar(1, AHORA);
            assertEquals(EstadoTicket.EN_PROGRESO, ticket.getEstado());
        }

        @Test void abiertoACancelado() {
            Ticket ticket = enEstado(EstadoTicket.ABIERTO);
            ticket.cancelar(SOLICITANTE, "Ya funciona", AHORA);
            assertEquals(EstadoTicket.CANCELADO, ticket.getEstado());
            assertEquals("Ya funciona", ticket.getHistorial().getLast().motivo());
        }

        @Test void enProgresoAEnEsperaYDeVuelta() {
            Ticket ticket = enEstado(EstadoTicket.EN_PROGRESO);
            ticket.ponerEnEspera(TECNICO, "Esperando foto del error", AHORA);
            assertEquals(EstadoTicket.EN_ESPERA, ticket.getEstado());
            ticket.iniciar(TECNICO, AHORA);
            assertEquals(EstadoTicket.EN_PROGRESO, ticket.getEstado());
            assertEquals(2, ticket.getHistorial().size());
        }

        @Test void enProgresoAResueltoGuardaSolucionYConocimiento() {
            Ticket ticket = enEstado(EstadoTicket.EN_PROGRESO);
            ticket.resolver("  Se cambió el tóner ", 7, TECNICO, AHORA);
            assertEquals(EstadoTicket.RESUELTO, ticket.getEstado());
            assertEquals("Se cambió el tóner", ticket.getSolucion());
            assertEquals(7, ticket.getConocimientoId());
            assertEquals(AHORA, ticket.getFechaResolucion());
        }

        @Test void resueltoACerrado() {
            Ticket ticket = enEstado(EstadoTicket.RESUELTO);
            ticket.cerrar(SOLICITANTE, null, AHORA);
            assertEquals(EstadoTicket.CERRADO, ticket.getEstado());
            assertEquals(AHORA, ticket.getFechaCierre());
        }

        @Test void resueltoAEnProgresoAlReabrir() {
            Ticket ticket = enEstado(EstadoTicket.RESUELTO);
            ticket.reabrir(SOLICITANTE, "Sigue fallando", AHORA);
            assertEquals(EstadoTicket.EN_PROGRESO, ticket.getEstado());
            assertNull(ticket.getFechaResolucion());
        }

        @Test void cadaCambioQuedaEnElHistorialConEstadoAnteriorYUsuario() {
            Ticket ticket = enEstado(EstadoTicket.EN_PROGRESO);
            ticket.ponerEnEspera(TECNICO, null, AHORA);
            HistorialEstadoTicket h = ticket.getHistorial().getLast();
            assertEquals(EstadoTicket.EN_PROGRESO, h.estadoAnterior());
            assertEquals(EstadoTicket.EN_ESPERA, h.estadoNuevo());
            assertEquals(TECNICO, h.usuarioId());
        }
    }

    @Nested
    class TransicionesInvalidas {
        @ParameterizedTest
        @EnumSource(value = EstadoTicket.class, names = "EN_PROGRESO", mode = EnumSource.Mode.EXCLUDE)
        void soloSeEsperaDesdeEnProgreso(EstadoTicket estado) {
            assertThrows(TransicionEstadoInvalidaException.class,
                    () -> enEstado(estado).ponerEnEspera(TECNICO, null, AHORA));
        }

        @ParameterizedTest
        @EnumSource(value = EstadoTicket.class, names = "EN_PROGRESO", mode = EnumSource.Mode.EXCLUDE)
        void soloSeResuelveDesdeEnProgreso(EstadoTicket estado) {
            assertThrows(TransicionEstadoInvalidaException.class,
                    () -> enEstado(estado).resolver("Solución", null, TECNICO, AHORA));
        }

        @ParameterizedTest
        @EnumSource(value = EstadoTicket.class, names = "RESUELTO", mode = EnumSource.Mode.EXCLUDE)
        void soloSeCierraDesdeResuelto(EstadoTicket estado) {
            assertThrows(TransicionEstadoInvalidaException.class, () -> enEstado(estado).cerrar(1, null, AHORA));
        }

        @ParameterizedTest
        @EnumSource(value = EstadoTicket.class, names = "RESUELTO", mode = EnumSource.Mode.EXCLUDE)
        void soloSeReabreDesdeResuelto(EstadoTicket estado) {
            assertThrows(TransicionEstadoInvalidaException.class, () -> enEstado(estado).reabrir(1, null, AHORA));
        }

        @ParameterizedTest
        @EnumSource(value = EstadoTicket.class, names = "ABIERTO", mode = EnumSource.Mode.EXCLUDE)
        void soloSeCancelaDesdeAbierto(EstadoTicket estado) {
            assertThrows(TransicionEstadoInvalidaException.class, () -> enEstado(estado).cancelar(1, null, AHORA));
        }

        @ParameterizedTest
        @EnumSource(value = EstadoTicket.class, names = {"ABIERTO", "EN_ESPERA"}, mode = EnumSource.Mode.EXCLUDE)
        void soloSeIniciaDesdeAbiertoOEnEspera(EstadoTicket estado) {
            assertThrows(TransicionEstadoInvalidaException.class, () -> enEstado(estado).iniciar(1, AHORA));
        }

        @ParameterizedTest
        @EnumSource(value = EstadoTicket.class, names = {"RESUELTO", "CERRADO", "CANCELADO"})
        void noSeAsignaUnTicketResueltoOFinalizado(EstadoTicket estado) {
            assertThrows(TransicionEstadoInvalidaException.class, () -> enEstado(estado).asignar(TECNICO, 1, AHORA));
        }

        @Test void resolverSinSolucionEsInvalido() {
            assertThrows(TicketInvalidoException.class,
                    () -> enEstado(EstadoTicket.EN_PROGRESO).resolver("  ", null, TECNICO, AHORA));
        }

        @Test void transicionFallidaNoModificaElTicket() {
            Ticket ticket = enEstado(EstadoTicket.CERRADO);
            assertThrows(TransicionEstadoInvalidaException.class, () -> ticket.reabrir(1, null, AHORA));
            assertEquals(EstadoTicket.CERRADO, ticket.getEstado());
            assertTrue(ticket.getHistorial().isEmpty());
        }
    }

    @Nested
    class TicketsFinalizados {
        @ParameterizedTest
        @EnumSource(value = EstadoTicket.class, names = {"CERRADO", "CANCELADO"})
        void noAdmitenComentarios(EstadoTicket estado) {
            assertThrows(TransicionEstadoInvalidaException.class,
                    () -> enEstado(estado).comentar(1, "Hola", false, AHORA));
        }

        @ParameterizedTest
        @EnumSource(value = EstadoTicket.class, names = {"CERRADO", "CANCELADO"})
        void noAdmitenEtiquetasNiConocimiento(EstadoTicket estado) {
            assertThrows(TransicionEstadoInvalidaException.class,
                    () -> enEstado(estado).etiquetar(Set.of(1L), AHORA));
            assertThrows(TransicionEstadoInvalidaException.class,
                    () -> enEstado(estado).vincularConocimiento(5, AHORA));
        }
    }

    @Nested
    class Comentarios {
        @Test void comentarioValido() {
            Ticket ticket = enEstado(EstadoTicket.EN_PROGRESO);
            ComentarioTicket c = ticket.comentar(TECNICO, " Revisando ", true, AHORA);
            assertEquals("Revisando", c.texto());
            assertTrue(c.interno());
            assertEquals(1, ticket.getComentarios().size());
        }

        @Test void comentarioVacioOLargoEsInvalido() {
            Ticket ticket = enEstado(EstadoTicket.ABIERTO);
            assertThrows(TicketInvalidoException.class, () -> ticket.comentar(1, " ", false, AHORA));
            assertThrows(TicketInvalidoException.class, () -> ticket.comentar(1, "x".repeat(1001), false, AHORA));
        }

        @Test void usuarioNoVeComentariosInternos() {
            Ticket ticket = enEstado(EstadoTicket.EN_PROGRESO);
            ticket.comentar(TECNICO, "Nota interna", true, AHORA);
            ticket.comentar(TECNICO, "¿Puedes reiniciar?", false, AHORA);
            ActorTicket usuario = new ActorTicket(SOLICITANTE, "Ana", RolTicket.USUARIO);
            ActorTicket soporte = new ActorTicket(TECNICO, "Tito", RolTicket.SOPORTE);
            assertEquals(1, ticket.comentariosVisiblesPara(usuario).size());
            assertEquals(2, ticket.comentariosVisiblesPara(soporte).size());
        }
    }

    @Test void usuarioSoloVeSusTicketsYSoporteVeTodos() {
        Ticket ticket = enEstado(EstadoTicket.ABIERTO);
        assertTrue(ticket.puedeVer(new ActorTicket(SOLICITANTE, "A", RolTicket.USUARIO)));
        assertFalse(ticket.puedeVer(new ActorTicket(99, "B", RolTicket.USUARIO)));
        assertTrue(ticket.puedeVer(new ActorTicket(99, "S", RolTicket.SOPORTE)));
        assertTrue(ticket.puedeVer(new ActorTicket(98, "Ad", RolTicket.ADMINISTRADOR)));
    }

    @Test void conteoCompletaLosEstadosQueNoVienen() {
        ConteoTickets c = new ConteoTickets(java.util.Map.of(EstadoTicket.ABIERTO, 3L, EstadoTicket.CERRADO, 2L), 1, 1);
        assertEquals(5L, c.total());
        assertEquals(0L, c.porEstado().get(EstadoTicket.EN_ESPERA));
    }
}
