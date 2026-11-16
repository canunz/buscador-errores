package cl.casol.backend.tickets.application.port.out;

import cl.casol.backend.tickets.domain.AdjuntoTicket;
import cl.casol.backend.tickets.domain.Etiqueta;
import cl.casol.backend.tickets.domain.Ticket;

import java.util.List;
import java.util.Map;

/** Detalle de un ticket leído de una sola vez, con nombres ya resueltos. */
public record DetalleTicketPlano(Ticket ticket, List<Etiqueta> etiquetas, Map<Integer, UsuarioTicket> usuarios,
        ClasificacionTicket clasificacion, ConocimientoReferencia conocimiento, List<AdjuntoTicket> adjuntos) {
}
