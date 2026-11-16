package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.domain.EstadoTicket;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CambiarEstadoTicketRequest(
        @NotNull(message = "Debe indicar el estado nuevo") EstadoTicket estado,
        @Size(max = 255, message = "El motivo no puede superar 255 caracteres") String motivo) {
}