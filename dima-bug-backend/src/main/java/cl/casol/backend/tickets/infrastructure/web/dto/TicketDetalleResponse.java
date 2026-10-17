package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.application.port.in.TicketVista;
import cl.casol.backend.tickets.application.port.out.ClasificacionTicket;
import cl.casol.backend.tickets.application.port.out.ConocimientoReferencia;
import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.PrioridadTicket;
import cl.casol.backend.tickets.domain.Ticket;

import java.time.LocalDateTime;
import java.util.List;

public record TicketDetalleResponse(Long id, String codigo, String titulo, String descripcion, EstadoTicket estado,
        PrioridadTicket prioridad, ReferenciaResponse solicitante, ReferenciaResponse asignado,
        ReferenciaResponse hardware, ReferenciaResponse sistema, ReferenciaResponse modulo,
        ConocimientoVinculadoResponse conocimiento, String solucion,
        LocalDateTime fechaCreacion, LocalDateTime fechaActualizacion, LocalDateTime fechaResolucion,
        LocalDateTime fechaCierre, List<EtiquetaResponse> etiquetas, List<ComentarioResponse> comentarios,
        List<HistorialResponse> historial, List<AdjuntoResponse> adjuntos) {

    public static TicketDetalleResponse from(TicketVista vista) {
        Ticket t = vista.ticket();
        ClasificacionTicket c = vista.clasificacion() == null ? ClasificacionTicket.VACIA : vista.clasificacion();
        return new TicketDetalleResponse(t.getId(), t.getCodigo(), t.getTitulo(), t.getDescripcion(), t.getEstado(),
                t.getPrioridad(),
                ReferenciaResponse.usuario(t.getSolicitanteId(), vista.usuario(t.getSolicitanteId())),
                ReferenciaResponse.usuario(t.getAsignadoId(), vista.usuario(t.getAsignadoId())),
                ReferenciaResponse.catalogo(t.getHardwareId(), c.hardware()),
                ReferenciaResponse.catalogo(t.getSistemaId(), c.sistema()),
                ReferenciaResponse.catalogo(t.getModuloId(), c.modulo()),
                ConocimientoVinculadoResponse.from(t.getConocimientoId(), vista.conocimiento()),
                t.getSolucion(), t.getFechaCreacion(), t.getFechaActualizacion(), t.getFechaResolucion(),
                t.getFechaCierre(),
                vista.etiquetas().stream().map(EtiquetaResponse::from).toList(),
                vista.comentarios().stream().map(cm -> new ComentarioResponse(cm.id(),
                        ReferenciaResponse.usuario(cm.autorId(), vista.usuario(cm.autorId())), cm.texto(),
                        cm.interno(), cm.fecha())).toList(),
                t.getHistorial().stream().map(h -> new HistorialResponse(h.id(), h.estadoAnterior(), h.estadoNuevo(),
                        ReferenciaResponse.usuario(h.usuarioId(), vista.usuario(h.usuarioId())), h.motivo(),
                        h.fecha())).toList(),
                vista.adjuntos().stream().map(a -> new AdjuntoResponse(a.id(), a.nombre(), a.mime(),
                        a.tipo().name(), a.tamano(), a.comentarioId(),
                        ReferenciaResponse.usuario(a.autorId(), vista.usuario(a.autorId())), a.fecha())).toList());
    }

    public record AdjuntoResponse(Long id, String nombre, String mime, String tipo, long tamano, Long comentarioId,
            ReferenciaResponse autor, LocalDateTime fecha) { }

    public record ComentarioResponse(Long id, ReferenciaResponse autor, String texto, boolean interno,
            LocalDateTime fecha) { }

    public record HistorialResponse(Long id, EstadoTicket estadoAnterior, EstadoTicket estadoNuevo,
            ReferenciaResponse usuario, String motivo, LocalDateTime fecha) { }

    public record ConocimientoVinculadoResponse(Integer id, String titulo, String estado) {
        static ConocimientoVinculadoResponse from(Integer id, ConocimientoReferencia referencia) {
            if (id == null) return null;
            return referencia == null ? new ConocimientoVinculadoResponse(id, null, null)
                    : new ConocimientoVinculadoResponse(referencia.id(), referencia.titulo(), referencia.estado());
        }
    }
}
