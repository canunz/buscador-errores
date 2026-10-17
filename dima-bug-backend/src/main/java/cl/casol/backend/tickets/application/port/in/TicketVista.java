package cl.casol.backend.tickets.application.port.in;

import cl.casol.backend.tickets.application.port.out.ClasificacionTicket;
import cl.casol.backend.tickets.application.port.out.ConocimientoReferencia;
import cl.casol.backend.tickets.application.port.out.UsuarioTicket;
import cl.casol.backend.tickets.domain.AdjuntoTicket;
import cl.casol.backend.tickets.domain.ComentarioTicket;
import cl.casol.backend.tickets.domain.Etiqueta;
import cl.casol.backend.tickets.domain.Ticket;

import java.util.List;
import java.util.Map;

/**
 * Ticket listo para mostrar: incluye nombres de usuarios, etiquetas y solo los comentarios
 * que la persona que consulta puede ver.
 */
public record TicketVista(Ticket ticket, List<Etiqueta> etiquetas, Map<Integer, UsuarioTicket> usuarios,
        ClasificacionTicket clasificacion, ConocimientoReferencia conocimiento,
        List<ComentarioTicket> comentarios, List<AdjuntoTicket> adjuntos) {

    public TicketVista(Ticket ticket, List<Etiqueta> etiquetas, Map<Integer, UsuarioTicket> usuarios,
            ClasificacionTicket clasificacion, ConocimientoReferencia conocimiento,
            List<ComentarioTicket> comentarios) {
        this(ticket, etiquetas, usuarios, clasificacion, conocimiento, comentarios, List.of());
    }

    public UsuarioTicket usuario(Integer id) {
        return id == null ? null : usuarios.get(id);
    }
}
