package cl.casol.backend.tickets.application.port.in;

import cl.casol.backend.tickets.domain.PrioridadTicket;

/**
 * Puerto de entrada: define la acción "cambiar prioridad ticket" que ofrece el módulo de tickets.
 * El controlador usa esta interfaz y el servicio la implementa.
 */
public interface CambiarPrioridadTicketUseCase {
    /** Soporte y el administrador pueden corregir la prioridad de un ticket que sigue activo. */
    TicketVista cambiarPrioridad(Long ticketId, PrioridadTicket prioridad, String motivo, String emailAutenticado);
}
