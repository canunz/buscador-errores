package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.domain.PrioridadTicket;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CambiarPrioridadRequest(
        @NotNull(message = "Debe indicar la prioridad nueva") PrioridadTicket prioridad,
        @Size(max = 180, message = "El motivo no puede superar 180 caracteres") String motivo) {
}
