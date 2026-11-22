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

    // Devuelve para básico.
    public TicketVista paraBasico() {
        Ticket t=ticket;
        Ticket seguro=new Ticket(t.getId(),t.getCodigo(),t.getTitulo(),t.getDescripcion(),t.getEstado(),t.getPrioridad(),
            t.getSolicitanteId(),null,null,null,null,null,null,t.getFechaCreacion(),t.getFechaActualizacion(),
            t.getFechaResolucion(),t.getFechaCierre(),java.util.Set.of(),List.of(),List.of());
        seguro.registrarDatosSolicitante(t.getDatosSolicitante());
        UsuarioTicket propio=usuarios.get(t.getSolicitanteId());
        return new TicketVista(seguro,List.of(),propio==null?Map.of():Map.of(t.getSolicitanteId(),propio),
            ClasificacionTicket.VACIA,null,List.of(),adjuntos.stream()
                .filter(a -> a.comentarioId()==null && java.util.Objects.equals(a.autorId(),t.getSolicitanteId()))
                .map(a -> new AdjuntoTicket(a.id(),a.ticketId(),null,a.nombre(),a.mime(),a.tamano(),null,null,a.fecha())).toList());
    }

    // Devuelve usuario.
    public UsuarioTicket usuario(Integer id) {
        return id == null ? null : usuarios.get(id);
    }
}
