package cl.casol.backend.tickets.infrastructure.persistence.mapper;

import cl.casol.backend.tickets.domain.ComentarioTicket;
import cl.casol.backend.tickets.domain.Etiqueta;
import cl.casol.backend.tickets.domain.HistorialEstadoTicket;
import cl.casol.backend.tickets.domain.Ticket;
import cl.casol.backend.tickets.infrastructure.persistence.entity.ComentarioTicketEntity;
import cl.casol.backend.tickets.infrastructure.persistence.entity.EtiquetaEntity;
import cl.casol.backend.tickets.infrastructure.persistence.entity.HistorialEstadoTicketEntity;
import cl.casol.backend.tickets.infrastructure.persistence.entity.TicketEntity;

import java.util.List;

public final class TicketMapper {
    private TicketMapper() { }

    public static TicketEntity toEntity(Ticket t) {
        return new TicketEntity(t.getId(), t.getCodigo(), t.getTitulo(), t.getDescripcion(), t.getEstado(),
                t.getPrioridad(), t.getSolicitanteId(), t.getAsignadoId(), t.getHardwareId(), t.getSistemaId(),
                t.getModuloId(), t.getConocimientoId(), t.getSolucion(), t.getFechaCreacion(),
                t.getFechaActualizacion(), t.getFechaResolucion(), t.getFechaCierre(), t.getEtiquetaIds());
    }

    public static Ticket toDomain(TicketEntity e, List<ComentarioTicket> comentarios,
            List<HistorialEstadoTicket> historial) {
        return new Ticket(e.getId(), e.getCodigo(), e.getTitulo(), e.getDescripcion(), e.getEstado(),
                e.getPrioridad(), e.getSolicitanteId(), e.getAsignadoId(), e.getHardwareId(), e.getSistemaId(),
                e.getModuloId(), e.getConocimientoId(), e.getSolucion(), e.getFechaCreacion(),
                e.getFechaActualizacion(), e.getFechaResolucion(), e.getFechaCierre(), e.getEtiquetaIds(),
                comentarios, historial);
    }

    public static ComentarioTicket toDomain(ComentarioTicketEntity e) {
        return new ComentarioTicket(e.getId(), e.getAutorId(), e.getTexto(), e.isInterno(), e.getFecha());
    }

    public static ComentarioTicketEntity toEntity(Long ticketId, ComentarioTicket c) {
        return new ComentarioTicketEntity(c.id(), ticketId, c.autorId(), c.texto(), c.interno(), c.fecha());
    }

    public static HistorialEstadoTicket toDomain(HistorialEstadoTicketEntity e) {
        return new HistorialEstadoTicket(e.getId(), e.getEstadoAnterior(), e.getEstadoNuevo(), e.getUsuarioId(),
                e.getMotivo(), e.getFecha());
    }

    public static HistorialEstadoTicketEntity toEntity(Long ticketId, HistorialEstadoTicket h) {
        return new HistorialEstadoTicketEntity(h.id(), ticketId, h.estadoAnterior(), h.estadoNuevo(), h.usuarioId(),
                h.motivo(), h.fecha());
    }

    public static Etiqueta toDomain(EtiquetaEntity e) {
        return new Etiqueta(e.getId(), e.getNombre(), e.getColor(), e.isActiva(), e.getNivel());
    }

    public static EtiquetaEntity toEntity(Etiqueta e) {
        return new EtiquetaEntity(e.getId(), e.getNombre(), e.getColor(), e.isActiva(), e.getNivel());
    }
}
