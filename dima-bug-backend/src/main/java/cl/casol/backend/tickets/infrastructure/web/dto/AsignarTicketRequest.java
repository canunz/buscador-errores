package cl.casol.backend.tickets.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

public record AsignarTicketRequest(@NotNull(message = "Debe indicar el técnico asignado") Integer asignadoId) {
}