package cl.casol.backend.tickets.application.port.in;

public interface AdjuntarArchivoTicketUseCase {
    /** comentarioId es opcional: sin él, el archivo queda junto a la descripción del ticket. */
    TicketVista adjuntar(Long ticketId, Long comentarioId, String nombre, byte[] contenido, String emailAutenticado);

    ArchivoAdjunto descargar(Long ticketId, Long adjuntoId, String emailAutenticado);

    record ArchivoAdjunto(String nombre, String mime, byte[] contenido) { }
}
