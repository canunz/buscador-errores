package cl.casol.backend.tickets.domain;

import java.time.LocalDateTime;

/**
 * Modelo de dominio de historial estado ticket. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record HistorialEstadoTicket(Long id, EstadoTicket estadoAnterior, EstadoTicket estadoNuevo,
        Integer usuarioId, String motivo, LocalDateTime fecha) {
}