package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.domain.ConteoTickets;
import cl.casol.backend.tickets.domain.EstadoTicket;

import java.util.Map;

public record ConteoTicketsResponse(Map<EstadoTicket, Long> porEstado, long total, long criticosActivos,
        long sinTecnico) {
    public static ConteoTicketsResponse from(ConteoTickets c) {
        return new ConteoTicketsResponse(c.porEstado(), c.total(), c.criticosActivos(), c.sinTecnico());
    }
}
