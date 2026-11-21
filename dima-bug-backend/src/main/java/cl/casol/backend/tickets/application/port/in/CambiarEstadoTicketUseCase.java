package cl.casol.backend.tickets.application.port.in;

import cl.casol.backend.tickets.domain.EstadoTicket;

/**
 * Puerto de entrada: define la acción "cambiar estado ticket" que ofrece el módulo de tickets.
 * El controlador usa esta interfaz y el servicio la implementa.
 */
public interface CambiarEstadoTicketUseCase {
    // Cambia estado.
    TicketVista cambiarEstado(Long ticketId, EstadoTicket estadoNuevo, String motivo, String emailAutenticado);
}