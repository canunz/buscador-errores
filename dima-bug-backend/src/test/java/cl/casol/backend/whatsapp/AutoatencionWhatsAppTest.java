package cl.casol.backend.whatsapp;

import cl.casol.backend.whatsapp.application.port.out.*;
import cl.casol.backend.whatsapp.application.service.AutoatencionWhatsAppService;
import cl.casol.backend.whatsapp.domain.*;
import cl.casol.backend.whatsapp.infrastructure.meta.FormatoWhatsApp;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static cl.casol.backend.whatsapp.domain.RespuestaCanal.Estado.*;
import static org.junit.jupiter.api.Assertions.*;

/** Flujo pedido: Hola → ayuda; foto → OCR → "reinicia el POS" → ¿se solucionó?; NO/sin solución → ticket. */
class AutoatencionWhatsAppTest {
    static final RespuestaCanal.Solucion POS = new RespuestaCanal.Solucion(7, "Reiniciar el POS de venta",
            "<p>1. Cierra la aplicación de caja.</p><p>2. Apaga y enciende el POS.</p>");
    List<String> buscadas = new ArrayList<>();
    List<CrearTicketCanalPort.Solicitud> tickets = new ArrayList<>();
    List<RespuestaCanal.Solucion> resultados = List.of(POS);
    List<RespuestaCanal.Candidato> ocr = List.of(new RespuestaCanal.Candidato("  Error POS-1043:  sin conexión con caja ", List.of()));
    RuntimeException falloTicket;
    Instant ahora = Instant.parse("2026-11-18T20:00:00Z");
    EstadoConversacion estado;

    RespuestaCanal enviar(MensajeCanal.Tipo tipo, String contenido) {
        Clock reloj = Clock.fixed(ahora, ZoneOffset.UTC);
        var s = new AutoatencionWhatsAppService(q -> { buscadas.add(q); return resultados; }, m -> ocr,
                sol -> { if (falloTicket != null) throw falloTicket; tickets.add(sol); return "TK-2026-0123"; }, reloj);
        var m = new MensajeCanal("waba", "numero", "qa-a", UUID.randomUUID().toString(), tipo, contenido);
        s.restaurar(m, estado);
        var r = s.procesar(m);
        estado = s.instantanea(m); // Simula el almacén durable entre mensajes.
        return r;
    }
    RespuestaCanal texto(String t) { return enviar(MensajeCanal.Tipo.TEXTO, t); }
    RespuestaCanal foto() { return enviar(MensajeCanal.Tipo.IMAGEN, "qa-media-legible"); }

    @Test void holaResponde() {
        for (String saludo : List.of("Hola", "hola!", "Buenas tardes", "HOLA DimaBug", "buenos días")) {
            estado = null;
            var r = texto(saludo);
            assertEquals(SALUDO, r.estado());
            assertTrue(r.mensaje().contains("¿En qué puedo ayudarte?"));
        }
        assertTrue(buscadas.isEmpty());
        assertNull(estado);
    }

    @Test void fotoDevuelveSolucionYPreguntaSiSeSoluciono() {
        var r = foto();
        assertEquals(RESULTADOS, r.estado());
        assertEquals(List.of("Error POS-1043: sin conexión con caja"), buscadas);
        assertNotNull(estado);
        String whatsapp = FormatoWhatsApp.texto(r);
        assertTrue(whatsapp.contains("Reiniciar el POS de venta"));
        assertTrue(whatsapp.contains("2. Apaga y enciende el POS."));
        assertFalse(whatsapp.contains("<p>"));
        assertTrue(whatsapp.contains("Responde *SÍ* o *NO*"));
        assertTrue(tickets.isEmpty());
    }

    @Test void siCierraElCasoSinTicket() {
        foto();
        assertEquals(SOLUCIONADO, texto("Sí, gracias").estado());
        assertNull(estado);
        assertTrue(tickets.isEmpty());
    }

    @Test void noLevantaTicketConLaSolucionProbada() {
        foto();
        var r = texto("No, sigue igual");
        assertEquals(TICKET_CREADO, r.estado());
        assertTrue(r.mensaje().contains("TK-2026-0123"));
        assertEquals(1, tickets.size());
        var t = tickets.getFirst();
        assertEquals("qa-a", t.participante());
        assertNotNull(t.mensajeId()); // Clave de idempotencia del ticket.
        assertEquals("IMAGEN", t.origen());
        assertEquals("Error POS-1043: sin conexión con caja", t.consulta());
        assertEquals(List.of("Reiniciar el POS de venta"), t.solucionesOfrecidas());
        assertNull(estado);
    }

    @Test void sinSolucionLevantaTicketDeInmediato() {
        resultados = List.of();
        var r = foto();
        assertEquals(TICKET_CREADO, r.estado());
        assertEquals(1, tickets.size());
        assertTrue(tickets.getFirst().solucionesOfrecidas().isEmpty());
    }

    @Test void textoLibreTambienBusca() {
        assertEquals(RESULTADOS, texto("La impresora fiscal no imprime boletas").estado());
        assertEquals(List.of("La impresora fiscal no imprime boletas"), buscadas);
    }

    @Test void fotoIlegibleNoCreaTicketYPideOtra() {
        ocr = List.of(new RespuestaCanal.Candidato("   ", List.of()));
        var r = foto();
        assertEquals(INVALIDO, r.estado());
        assertTrue(r.mensaje().contains("más nítida"));
        assertTrue(buscadas.isEmpty());
        assertTrue(tickets.isEmpty());
    }

    @Test void falloDeTicketSeReintentaYConservaLaVerificacion() {
        foto();
        var pendiente = estado;
        falloTicket = new IllegalStateException("privado");
        var r = texto("no");
        assertEquals(ERROR_TICKET, r.estado());
        assertFalse(r.mensaje().contains("privado"));
        // En un reintento el almacén restaura el estado anterior al mensaje.
        estado = pendiente; falloTicket = null;
        assertEquals(TICKET_CREADO, texto("no").estado());
    }

    @Test void verificacionVence() {
        foto();
        ahora = ahora.plus(AutoatencionWhatsAppService.VIGENCIA_VERIFICACION).plusSeconds(1);
        assertEquals(SALUDO, texto("hola").estado());
        assertTrue(tickets.isEmpty());
    }

    @Test void otraConsultaReemplazaLaVerificacion() {
        foto();
        assertEquals(RESULTADOS, texto("No puedo ingresar al sistema de ventas").estado());
        assertEquals(2, buscadas.size());
        assertTrue(tickets.isEmpty());
    }

    @Test void tipoNoCompatible() {
        assertEquals(NO_COMPATIBLE, enviar(MensajeCanal.Tipo.NO_COMPATIBLE, "").estado());
    }

    @Test void destinatarioEsAliasNoTelefono() {
        var r = RespuestaCanal.simple(SALUDO, "x").conDestinatario("qa-a");
        assertEquals("qa-a", r.destinatario());
        assertNull(RespuestaCanal.simple(SALUDO, "x").destinatario());
    }
}
