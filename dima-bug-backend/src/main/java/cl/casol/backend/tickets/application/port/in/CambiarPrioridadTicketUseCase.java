package cl.casol.backend.tickets.application.port.in;

import cl.casol.backend.tickets.domain.PrioridadTicket;

public interface CambiarPrioridadTicketUseCase {
    /** Soporte y el administrador pueden corregir la prioridad de un ticket que sigue activo. */
    TicketVista cambiarPrioridad(Long ticketId, PrioridadTicket prioridad, String motivo, String emailAutenticado);
}
