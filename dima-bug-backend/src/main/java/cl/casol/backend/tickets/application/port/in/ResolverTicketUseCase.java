package cl.casol.backend.tickets.application.port.in;

public interface ResolverTicketUseCase {
    TicketVista resolver(Long ticketId, String solucion, Integer conocimientoId, String emailAutenticado);

    /** Crea un conocimiento en BORRADOR prellenado con el ticket y lo deja vinculado. */
    TicketVista crearConocimientoDesdeTicket(Long ticketId, String emailAutenticado);
}