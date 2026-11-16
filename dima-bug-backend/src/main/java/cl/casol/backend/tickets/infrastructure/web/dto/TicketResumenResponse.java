package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.PrioridadTicket;
import cl.casol.backend.tickets.domain.Ticket;

import java.time.LocalDateTime;
import java.util.List;

public record TicketResumenResponse(Long id, String codigo, String titulo, EstadoTicket estado,
        PrioridadTicket prioridad, ReferenciaResponse solicitante, ReferenciaResponse asignado,
        List<EtiquetaResponse> etiquetas, LocalDateTime fechaCreacion, LocalDateTime fechaActualizacion) {

    public static TicketResumenResponse from(TicketVista vista) {
        Ticket t = vista.ticket();
        return new TicketResumenResponse(t.getId(), t.getCodigo(), t.getTitulo(), t.getEstado(), t.getPrioridad(),
                ReferenciaResponse.usuario(t.getSolicitanteId(), vista.usuario(t.getSolicitanteId())),
                ReferenciaResponse.usuario(t.getAsignadoId(), vista.usuario(t.getAsignadoId())),
                vista.etiquetas().stream().map(EtiquetaResponse::from).toList(),
                t.getFechaCreacion(), t.getFechaActualizacion());
    }
}