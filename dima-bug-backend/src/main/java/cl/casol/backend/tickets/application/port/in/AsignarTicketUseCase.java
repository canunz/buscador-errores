package cl.casol.backend.tickets.application.port.in;

public interface AsignarTicketUseCase {
    /** Soporte solo puede asignarse a sí mismo; el administrador puede asignar a cualquier técnico. */
    TicketVista asignar(Long ticketId, Integer asignadoId, String emailAutenticado);
}