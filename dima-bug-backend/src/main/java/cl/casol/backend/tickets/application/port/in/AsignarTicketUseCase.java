package cl.casol.backend.tickets.application.port.in;

/**
 * Puerto de entrada: define la acción "asignar ticket" que ofrece el módulo de tickets.
 * El controlador usa esta interfaz y el servicio la implementa.
 */
public interface AsignarTicketUseCase {
    /** Soporte solo puede asignarse a sí mismo; el administrador puede asignar a cualquier técnico. */
    TicketVista asignar(Long ticketId, Integer asignadoId, String emailAutenticado);
}