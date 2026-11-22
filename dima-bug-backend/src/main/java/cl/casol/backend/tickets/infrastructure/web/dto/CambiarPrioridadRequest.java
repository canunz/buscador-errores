package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.domain.PrioridadTicket;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos que llegan en la petición para cambiar prioridad.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record CambiarPrioridadRequest(
        @NotNull(message = "Debe indicar la prioridad nueva") PrioridadTicket prioridad,
        @Size(max = 180, message = "El motivo no puede superar 180 caracteres") String motivo) {
}
