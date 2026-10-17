package cl.casol.backend.tickets.application.port.in;

import cl.casol.backend.tickets.domain.EstadoTicket;

public interface CambiarEstadoTicketUseCase {
    TicketVista cambiarEstado(Long ticketId, EstadoTicket estadoNuevo, String motivo, String emailAutenticado);
}