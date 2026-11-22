package cl.casol.backend.whatsapp.infrastructure.tickets;

import cl.casol.backend.tickets.application.port.in.*;
import cl.casol.backend.tickets.domain.*;
import cl.casol.backend.whatsapp.application.port.out.CrearTicketCanalPort;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Un reintento del mismo mensaje de WhatsApp no debe crear un segundo ticket. */
class TicketWhatsAppAdapterTest {
    final AtomicInteger creados = new AtomicInteger();
    final List<String> descripciones = Collections.synchronizedList(new ArrayList<>());
    RuntimeException fallo;
    Instant ahora = Instant.parse("2026-11-18T22:00:00Z");
    final Clock reloj = new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId z) { return this; }
        public Instant instant() { return ahora; }
    };
    final CrearTicketUseCase caso = (c, email) -> {
        if (fallo != null) throw fallo;
        int n = creados.incrementAndGet();
        descripciones.add(c.descripcion());
        var t = Ticket.crear(Ticket.generarCodigo(2026, n), c.titulo(), c.descripcion(), c.prioridad(), 9,
                null, null, null, LocalDateTime.of(2026, 11, 18, 19, 0));
        return new TicketVista(t, List.of(), Map.of(), null, null, List.of(), List.of());
    };
    final TicketWhatsAppAdapter adaptador = new TicketWhatsAppAdapter(new cl.casol.backend.tickets.api.TicketsFachada(caso), "bot.whatsapp@dimarsa.cl", reloj);

    CrearTicketCanalPort.Solicitud solicitud(String id) {
        return new CrearTicketCanalPort.Solicitud(id, "qa-carolina", "Error POS-1043", "IMAGEN", List.of("Reiniciar el POS de venta"));
    }

    @Test void reintentoDelMismoMensajeDevuelveElMismoTicket() {
        assertEquals("TK-2026-0001", adaptador.crear(solicitud("wamid.A")));
        assertEquals("TK-2026-0001", adaptador.crear(solicitud("wamid.A")));
        assertEquals(1, creados.get());
    }
    @Test void mensajesDistintosCreanTicketsDistintos() {
        assertEquals("TK-2026-0001", adaptador.crear(solicitud("wamid.A")));
        assertEquals("TK-2026-0002", adaptador.crear(solicitud("wamid.B")));
        assertEquals(2, creados.get());
    }
    @Test void falloNoQuedaRegistradoYElReintentoCrea() {
        fallo = new IllegalStateException("bd caída");
        assertThrows(CrearTicketCanalPort.FalloTicket.class, () -> adaptador.crear(solicitud("wamid.A")));
        fallo = null;
        assertEquals("TK-2026-0001", adaptador.crear(solicitud("wamid.A")));
        assertEquals(1, creados.get());
    }
    @Test void trabajadoresConcurrentesCreanUnSoloTicket() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        var inicio = new CountDownLatch(1);
        var futuros = new ArrayList<Future<String>>();
        for (int i = 0; i < 8; i++) futuros.add(pool.submit(() -> { inicio.await(); return adaptador.crear(solicitud("wamid.A")); }));
        inicio.countDown();
        for (var f : futuros) assertEquals("TK-2026-0001", f.get(5, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(1, creados.get());
    }
    @Test void vencidaLaVigenciaSePermiteUnTicketNuevo() {
        adaptador.crear(solicitud("wamid.A"));
        ahora = ahora.plus(TicketWhatsAppAdapter.VIGENCIA).plusSeconds(1);
        assertEquals("TK-2026-0002", adaptador.crear(solicitud("wamid.A")));
    }
    @Test void descripcionLlevaReferenciaSinExponerElWamid() {
        adaptador.crear(solicitud("wamid.HBgLNTY5OTY4MDc4MjUVAgAS"));
        String d = descripciones.getFirst();
        assertTrue(d.contains("Referencia WhatsApp: " + TicketWhatsAppAdapter.referencia("wamid.HBgLNTY5OTY4MDc4MjUVAgAS")));
        assertFalse(d.contains("wamid."));
        assertEquals(16, TicketWhatsAppAdapter.referencia("x").length());
    }
    @Test void sinIdentificadorNoCrea() {
        assertThrows(CrearTicketCanalPort.FalloTicket.class, () -> adaptador.crear(solicitud(" ")));
        assertEquals(0, creados.get());
    }
}
