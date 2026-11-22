package cl.casol.backend.whatsapp.infrastructure.tickets;

import cl.casol.backend.tickets.application.port.in.CrearTicketUseCase;
import cl.casol.backend.tickets.domain.PrioridadTicket;
import cl.casol.backend.tickets.domain.Ticket;
import cl.casol.backend.whatsapp.application.port.out.CrearTicketCanalPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crea el ticket con el caso de uso existente, a nombre de un usuario de servicio de Soporte
 * (rol staff, no administrador). El ticket registra el alias QA del participante, nunca su teléfono.
 *
 * Idempotencia: el mismo mensaje de WhatsApp produce como máximo un ticket. Un reintento del evento
 * (reserva vencida, fallo al finalizar, plazo de 45 s) devuelve el código ya creado durante 24 h.
 * La memoria vive en el proceso; tras un reinicio queda la referencia en la descripción para revisión.
 */
public final class TicketWhatsAppAdapter implements CrearTicketCanalPort {
    static final Duration VIGENCIA = Duration.ofHours(24);
    static final int MAX_CREADOS = 10_000;
    private record Creado(String codigo, Instant vence) { }
    private final CrearTicketUseCase crear;
    private final String actorEmail;
    private final Clock reloj;
    private final ConcurrentHashMap<String, Creado> creados = new ConcurrentHashMap<>();
    public TicketWhatsAppAdapter(CrearTicketUseCase crear, String actorEmail) { this(crear, actorEmail, Clock.systemUTC()); }
    TicketWhatsAppAdapter(CrearTicketUseCase crear, String actorEmail, Clock reloj) {
        if (actorEmail == null || !actorEmail.matches("[^@\\s]{1,64}@[^@\\s]{1,190}"))
            throw new IllegalArgumentException("Configura dimabug.whatsapp.autoatencion.ticket-actor-email");
        this.crear = crear; this.actorEmail = actorEmail; this.reloj = reloj;
    }
    @Override public String crear(Solicitud s) {
        if (s.mensajeId() == null || s.mensajeId().isBlank()) throw new FalloTicket();
        Instant ahora = reloj.instant();
        if (creados.size() >= MAX_CREADOS) creados.values().removeIf(c -> !c.vence().isAfter(ahora));
        String clave = referencia(s.mensajeId());
        // compute bloquea la clave: dos trabajadores con el mismo mensaje no crean dos tickets.
        return creados.compute(clave, (k, previo) -> previo != null && previo.vence().isAfter(ahora)
                ? previo : new Creado(registrar(s, k), ahora.plus(VIGENCIA))).codigo();
    }
    /** Huella corta del identificador de WhatsApp: trazable sin exponer el wamid. */
    static String referencia(String mensajeId) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(mensajeId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 8);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String registrar(Solicitud s, String referencia) {
        String consulta = s.consulta() == null ? "" : s.consulta().strip();
        String titulo = "WhatsApp: " + (consulta.isEmpty() ? "consulta sin texto" : consulta);
        if (titulo.length() > Ticket.MAX_TITULO) titulo = titulo.substring(0, Ticket.MAX_TITULO - 1).strip() + "…";
        var d = new StringBuilder("Ticket creado automáticamente por la autoatención de WhatsApp.\n\n")
                .append("Participante (alias QA): ").append(s.participante()).append('\n')
                .append("Referencia WhatsApp: ").append(referencia).append('\n')
                .append("Origen: ").append("IMAGEN".equals(s.origen()) ? "foto del error leída con OCR" : "mensaje de texto").append('\n')
                .append("Fecha: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))).append("\n\n")
                .append(consulta.isEmpty() ? "" : "Error / consulta:\n" + consulta + "\n\n");
        if (s.solucionesOfrecidas().isEmpty()) d.append("No se encontró una solución de autoatención en la base de conocimiento.");
        else {
            d.append("Soluciones sugeridas que NO resolvieron el problema:\n");
            s.solucionesOfrecidas().forEach(t -> d.append("- ").append(t).append('\n'));
        }
        try {
            var vista = crear.crear(new CrearTicketUseCase.Comando(titulo, d.toString().strip(), PrioridadTicket.MEDIA,
                    null, null, null), actorEmail);
            return vista.ticket().getCodigo();
        } catch (RuntimeException e) { throw new FalloTicket(); }
    }
}
