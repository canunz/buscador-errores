package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.domain.ConteoTickets;
import cl.casol.backend.tickets.domain.EstadoTicket;

import java.util.Map;

/**
 * Datos que se devuelven al frontend con conteo tickets.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record ConteoTicketsResponse(Map<EstadoTicket, Long> porEstado, long total, long criticosActivos,
        long sinTecnico) {
    // Crea el objeto a partir de los datos recibidos.
    public static ConteoTicketsResponse from(ConteoTickets c) {
        return new ConteoTicketsResponse(c.porEstado(), c.total(), c.criticosActivos(), c.sinTecnico());
    }
}
