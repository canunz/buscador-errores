package cl.casol.backend.whatsapp.application.service;

import cl.casol.backend.whatsapp.application.port.out.*;
import cl.casol.backend.whatsapp.domain.*;
import static cl.casol.backend.whatsapp.domain.RespuestaCanal.Estado.*;
import java.text.Normalizer;
import java.time.*;
import java.util.*;

/**
 * Autoatención por WhatsApp:
 * <ol>
 *   <li>"Hola" → "¿En qué puedo ayudarte?"</li>
 *   <li>Foto del error → OCR → búsqueda en la base de conocimiento → solución sugerida
 *       y pregunta "¿Se solucionó? SÍ / NO".</li>
 *   <li>Texto libre → misma búsqueda.</li>
 *   <li>Sin solución, o respuesta NO → ticket en DimaBug.</li>
 * </ol>
 * La propuesta pendiente se guarda en {@link EstadoConversacion}: {@code consulta} es lo buscado y
 * {@code candidatos[0]} = Candidato(origen, títulos ofrecidos). Una instancia por reserva durable.
 */
public final class AutoatencionWhatsAppService implements ConversacionCanal {
    public static final Duration VIGENCIA_VERIFICACION = Duration.ofMinutes(30);
    static final int MAX_CONSULTA = 300;
    private static final Set<String> SALUDOS = Set.of("hola","ola","holi","holaa","buenas","buenos","buen","dia","dias",
        "tarde","tardes","noche","noches","hi","hello","hey","saludos","menu","inicio","ayuda","dimabug","que","tal","alo");
    private static final Set<String> SI = Set.of("si","sii","sip","ok","oka","okey","listo","lista","solucionado","solucionada",
        "resuelto","resuelta","funciono","funciona","gracias","yes","perfecto","excelente","1");
    private static final Set<String> NO = Set.of("no","nop","noo","nada","sigue","persiste","tampoco","2");

    private record Pendiente(String referencia, String consulta, String origen, List<String> ofrecidas, Instant vence) { }

    private final BuscarSolucionesCanalPort buscador;
    private final ReconocerImagenPort ocr;
    private final CrearTicketCanalPort tickets;
    private final Clock reloj;
    private Pendiente pendiente;
    private boolean usado;

    public AutoatencionWhatsAppService(BuscarSolucionesCanalPort buscador, ReconocerImagenPort ocr,
                                       CrearTicketCanalPort tickets, Clock reloj) {
        this.buscador = Objects.requireNonNull(buscador); this.ocr = Objects.requireNonNull(ocr);
        this.tickets = Objects.requireNonNull(tickets); this.reloj = Objects.requireNonNull(reloj);
    }

    @Override public synchronized void restaurar(MensajeCanal m, EstadoConversacion estado) {
        if (usado) throw new IllegalStateException("Procesador ya utilizado");
        usado = true;
        if (estado == null || estado.candidatos().isEmpty()) return;
        var c = estado.candidatos().getFirst();
        pendiente = new Pendiente(estado.referencia(), estado.consulta(), c.texto(), c.avisos(),
                Instant.ofEpochMilli(estado.venceEpochMilli()));
    }

    @Override public synchronized EstadoConversacion instantanea(MensajeCanal m) {
        var p = pendiente;
        return p == null ? null : new EstadoConversacion(p.referencia(), p.consulta(),
                List.of(new RespuestaCanal.Candidato(p.origen(), p.ofrecidas())), p.vence().toEpochMilli());
    }

    @Override public synchronized RespuestaCanal procesar(MensajeCanal m) {
        usado = true;
        if (pendiente != null && !pendiente.vence().isAfter(reloj.instant())) pendiente = null;
        return switch (m.tipo()) {
            case NO_COMPATIBLE -> simple(NO_COMPATIBLE,
                    "Por ahora puedo leer mensajes de texto y fotos 📷. Describe el problema o envíame una foto del error.");
            case IMAGEN -> imagen(m);
            case TEXTO -> texto(m);
        };
    }

    private RespuestaCanal imagen(MensajeCanal m) {
        pendiente = null; // Una foto nueva siempre inicia un caso nuevo.
        List<RespuestaCanal.Candidato> candidatos;
        try { candidatos = ocr.reconocer(m.contenido()); }
        catch (ReconocerImagenPort.PlazoOcr e) { return simple(TIEMPO_AGOTADO, "La lectura de la imagen tardó demasiado. Intenta con otra foto."); }
        catch (RuntimeException e) { return simple(ERROR_OCR, "No fue posible leer la imagen. Intenta con otra foto o describe el error."); }
        String leido = candidatos == null ? "" : candidatos.stream().map(c -> limpiar(c.texto()))
                .filter(t -> !t.isEmpty()).findFirst().orElse("");
        if (leido.isEmpty())
            return simple(INVALIDO, "No pude leer el mensaje de error en la foto. 🔍 Envía una imagen más nítida "
                    + "(sin reflejos y con la pantalla completa) o descríbeme el problema en un mensaje.");
        return resolver(m, recortar(leido), "IMAGEN");
    }

    private RespuestaCanal texto(MensajeCanal m) {
        String texto = m.contenido() == null ? "" : m.contenido().strip();
        if (texto.isEmpty() || texto.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
            return simple(INVALIDO, "Escríbeme el problema que tienes o envíame una foto del error.");
        String n = normalizar(texto);
        List<String> palabras = n.isEmpty() ? List.of() : List.of(n.split(" "));
        if (pendiente != null) {
            if (esNegativo(n, palabras)) { var p = pendiente; return ticket(m, p.consulta(), p.origen(), p.ofrecidas()); }
            if (esAfirmativo(palabras)) {
                pendiente = null;
                return simple(SOLUCIONADO, "¡Excelente! 🎉 Me alegra que se haya solucionado. "
                        + "Si necesitas algo más, escribe *Hola*.");
            }
            pendiente = null; // Cualquier otro texto es una consulta nueva.
        }
        if (!palabras.isEmpty() && palabras.size() <= 6 && SALUDOS.containsAll(palabras))
            return simple(SALUDO, "¡Hola! 👋 Soy *DimaBug*, el asistente de Soporte TI de Dimarsa.\n"
                    + "¿En qué puedo ayudarte?\n\nPuedes describir el problema o enviarme una *foto del error* 📷.");
        if (texto.length() > MAX_CONSULTA)
            return simple(INVALIDO, "Describe el problema en un mensaje más breve (máximo " + MAX_CONSULTA + " caracteres) o envía una foto del error.");
        return resolver(m, texto, "TEXTO");
    }

    private RespuestaCanal resolver(MensajeCanal m, String consulta, String origen) {
        List<RespuestaCanal.Solucion> soluciones;
        try { soluciones = List.copyOf(buscador.buscar(consulta)); }
        catch (FalloCatalogoPublico e) { throw e; }
        catch (RuntimeException e) { return simple(ERROR_BUSCADOR, "No fue posible consultar la base de conocimiento. Intenta nuevamente en unos minutos."); }
        if (soluciones.isEmpty()) return ticket(m, consulta, origen, List.of());
        var titulos = soluciones.stream().limit(2).map(s -> s.titulo() == null ? "" : s.titulo()).toList();
        pendiente = new Pendiente(UUID.randomUUID().toString(), consulta, origen, titulos,
                reloj.instant().plus(VIGENCIA_VERIFICACION));
        String intro = origen.equals("IMAGEN")
                ? "Revisé tu foto y detecté este error:\n_" + consulta + "_"
                : "Encontré una solución para tu consulta.";
        return new RespuestaCanal(RESULTADOS, intro, pendiente.referencia(), soluciones, List.of(), List.of());
    }

    private RespuestaCanal ticket(MensajeCanal m, String consulta, String origen, List<String> ofrecidas) {
        String codigo;
        try { codigo = tickets.crear(new CrearTicketCanalPort.Solicitud(m.id(), m.usuario(), consulta, origen, ofrecidas)); }
        catch (RuntimeException e) { return simple(ERROR_TICKET, "No pude registrar tu ticket en este momento. Lo intentaré nuevamente."); }
        pendiente = null;
        String motivo = ofrecidas.isEmpty() ? "No encontré una solución de autoatención para este problema."
                : "Lamento que la solución no haya funcionado.";
        return simple(TICKET_CREADO, motivo + "\n\n🎫 Levanté el ticket *" + codigo + "* en DimaBug. "
                + "Soporte TI revisará tu caso y te contactará.");
    }

    private static boolean esAfirmativo(List<String> p) {
        return !p.isEmpty() && SI.contains(p.getFirst()) && !p.contains("no");
    }
    private static boolean esNegativo(String n, List<String> p) {
        // "No, sigue igual" es una respuesta; "No puedo ingresar al sistema de ventas" es una consulta nueva.
        return !p.isEmpty() && ((NO.contains(p.getFirst()) && p.size() <= 4) || n.contains("no funciono") || n.contains("no funciona")
                || n.contains("no se soluciono") || n.contains("sigue igual") || n.contains("no resulto"));
    }
    static String normalizar(String t) {
        String sin = Normalizer.normalize(t.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sin.replaceAll("[^a-z0-9]+", " ").strip();
    }
    private static String limpiar(String t) { return t == null ? "" : t.replaceAll("\\s+", " ").strip(); }
    private static String recortar(String t) { return t.length() <= MAX_CONSULTA ? t : t.substring(0, MAX_CONSULTA).strip(); }
    private static RespuestaCanal simple(RespuestaCanal.Estado e, String mensaje) { return RespuestaCanal.simple(e, mensaje); }
}
