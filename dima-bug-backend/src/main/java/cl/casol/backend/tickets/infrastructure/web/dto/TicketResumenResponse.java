package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.PrioridadTicket;
import cl.casol.backend.tickets.domain.Ticket;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Datos que se devuelven al frontend con ticket resumen.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record TicketResumenResponse(Long id, String codigo, String titulo, EstadoTicket estado,
        PrioridadTicket prioridad, ReferenciaResponse solicitante, ReferenciaResponse asignado,
        List<EtiquetaResponse> etiquetas, LocalDateTime fechaCreacion, LocalDateTime fechaActualizacion) {

    // Crea el objeto a partir de los datos recibidos.
    public static TicketResumenResponse from(TicketVista vista) {
        Ticket t = vista.ticket();
        return new TicketResumenResponse(t.getId(), t.getCodigo(), t.getTitulo(), t.getEstado(), t.getPrioridad(),
                ReferenciaResponse.usuario(t.getSolicitanteId(), vista.usuario(t.getSolicitanteId())),
                ReferenciaResponse.usuario(t.getAsignadoId(), vista.usuario(t.getAsignadoId())),
                vista.etiquetas().stream().map(EtiquetaResponse::from).toList(),
                t.getFechaCreacion(), t.getFechaActualizacion());
    }
}