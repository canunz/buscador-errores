package cl.casol.backend.tickets.application.port.in;

/**
 * Puerto de entrada: define la acción "resolver ticket" que ofrece el módulo de tickets.
 * El controlador usa esta interfaz y el servicio la implementa.
 */
public interface ResolverTicketUseCase {
    // Resuelve el ticket indicado.
    TicketVista resolver(Long ticketId, String solucion, Integer conocimientoId, String emailAutenticado);

    /** Crea un conocimiento en BORRADOR prellenado con el ticket y lo deja vinculado. */
    TicketVista crearConocimientoDesdeTicket(Long ticketId, String emailAutenticado);
}