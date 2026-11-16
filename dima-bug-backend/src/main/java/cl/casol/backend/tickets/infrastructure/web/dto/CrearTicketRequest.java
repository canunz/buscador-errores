package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.domain.PrioridadTicket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearTicketRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 150, message = "El título no puede superar 150 caracteres") String titulo,
        @NotBlank(message = "La descripción es obligatoria") String descripcion,
        PrioridadTicket prioridad,
        Integer hardwareId,
        Integer sistemaId,
        Integer moduloId) {
}