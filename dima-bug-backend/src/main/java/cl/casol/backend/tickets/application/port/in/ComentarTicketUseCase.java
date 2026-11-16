package cl.casol.backend.tickets.application.port.in;

public interface ComentarTicketUseCase {
    TicketVista comentar(Long ticketId, String texto, boolean interno, String emailAutenticado);
}