package cl.casol.backend.tickets.application.port.in;

/**
 * Puerto de entrada: define la acción "comentar ticket" que ofrece el módulo de tickets.
 * El controlador usa esta interfaz y el servicio la implementa.
 */
public interface ComentarTicketUseCase {
    // Comenta el ticket indicado.
    TicketVista comentar(Long ticketId, String texto, boolean interno, String emailAutenticado);
}