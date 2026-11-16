package cl.casol.backend.tickets.domain;

import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;

import java.time.LocalDateTime;

/**
 * Archivo (imagen, video o PDF) subido a un ticket. Si va asociado a un comentario,
 * se ve con la misma regla del comentario (los de notas internas solo los ve soporte).
 */
public record AdjuntoTicket(Long id, Long ticketId, Long comentarioId, String nombre, String mime, long tamano,
        String referencia, Integer autorId, LocalDateTime fecha) {

    public static final long MAX_BYTES = 25L * 1024 * 1024;
    public static final int MAX_NOMBRE = 150;

    public static AdjuntoTicket nuevo(Long ticketId, Long comentarioId, String nombre, byte[] contenido,
            String referencia, Integer autorId, LocalDateTime fecha) {
        validarContenido(contenido);
        String mime = FormatoAdjunto.mime(contenido);
        return new AdjuntoTicket(null, ticketId, comentarioId, limpiarNombre(nombre, mime), mime, contenido.length,
                referencia, autorId, fecha);
    }

    /** Se llama antes de guardar el archivo en disco, para no almacenar nada inválido. */
    public static void validarContenido(byte[] contenido) {
        if (contenido == null || contenido.length == 0) throw new TicketInvalidoException("El archivo está vacío");
        if (contenido.length > MAX_BYTES) throw new TicketInvalidoException("Cada archivo puede pesar hasta 25 MB");
        FormatoAdjunto.mime(contenido);
    }

    public TipoAdjunto tipo() {
        return FormatoAdjunto.tipo(mime);
    }

    public boolean visiblePara(ActorTicket actor, Ticket ticket) {
        if (comentarioId == null) return true;
        return ticket.getComentarios().stream()
                .anyMatch(c -> comentarioId.equals(c.id()) && c.visiblePara(actor));
    }

    private static String limpiarNombre(String nombre, String mime) {
        String base = nombre == null ? "" : nombre.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").strip();
        if (base.isEmpty()) base = "archivo." + mime.substring(mime.indexOf('/') + 1);
        return base.length() > MAX_NOMBRE ? base.substring(base.length() - MAX_NOMBRE) : base;
    }
}
