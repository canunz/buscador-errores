package cl.casol.backend.tickets.application.port.in;

/**
 * Puerto de entrada: define la acción "adjuntar archivo ticket" que ofrece el módulo de tickets.
 * El controlador usa esta interfaz y el servicio la implementa.
 */
public interface AdjuntarArchivoTicketUseCase {
    /** comentarioId es opcional: sin él, el archivo queda junto a la descripción del ticket. */
    TicketVista adjuntar(Long ticketId, Long comentarioId, String nombre, byte[] contenido, String emailAutenticado);

    // Descarga el ticket indicado.
    ArchivoAdjunto descargar(Long ticketId, Long adjuntoId, String emailAutenticado);

    // Devuelve archivo adjunto.
    record ArchivoAdjunto(String nombre, String mime, byte[] contenido) { }
}
