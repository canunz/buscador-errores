package cl.casol.backend.tickets.domain;

import java.time.LocalDateTime;

public record HistorialEstadoTicket(Long id, EstadoTicket estadoAnterior, EstadoTicket estadoNuevo,
        Integer usuarioId, String motivo, LocalDateTime fecha) {
}